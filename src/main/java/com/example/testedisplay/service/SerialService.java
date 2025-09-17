package com.example.testedisplay.service;

import com.fazecast.jSerialComm.SerialPort;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Serviço avançado para comunicação serial com reconexão automática,
 * monitoramento de conexão e estatísticas detalhadas.
 */
public class SerialService {
    private SerialPort port;
    private Thread readerThread;
    private volatile boolean isReading = false;
    
    // Estatísticas
    private final AtomicLong totalBytesSent = new AtomicLong(0);
    private final AtomicLong totalBytesReceived = new AtomicLong(0);
    private final AtomicInteger successfulSends = new AtomicInteger(0);
    private final AtomicInteger failedSends = new AtomicInteger(0);
    
    // Configurações
    private String currentPortName;
    private int currentBaudRate;
    private int currentDataBits = 8;
    private int currentStopBits = SerialPort.ONE_STOP_BIT;
    private int currentParity = SerialPort.NO_PARITY;
    private int currentFlowControl = SerialPort.FLOW_CONTROL_DISABLED;
    
    // Callbacks para eventos
    private SerialEventListener eventListener;

    private LogService logService;

    public enum PortStatus {
        FREE, BUSY, NOT_FOUND, ERROR
    }
    
    // Interface para eventos seriais
    
    // Interface para eventos seriais
    public interface SerialEventListener {
        void onDataReceived(String data);
        void onConnectionStatusChanged(boolean connected, String portName);
        void onError(String errorMessage);
    }

    /**
     * Conecta na porta serial com parâmetros padrão.
     */
    public boolean connect(String portName, int baudRate, LogService log) {
        return connect(portName, baudRate, 8, SerialPort.ONE_STOP_BIT, 
                      SerialPort.NO_PARITY, SerialPort.FLOW_CONTROL_DISABLED, log);
    }

    /**
     * Conecta na porta serial com parâmetros completos.
     */
    public boolean connect(String portName, int baudRate, int dataBits, 
                          int stopBits, int parity, int flowControl, LogService log) {
        try {
            disconnect(); // Garante que não há conexão anterior
            
            port = SerialPort.getCommPort(portName);
            port.setComPortParameters(baudRate, dataBits, stopBits, parity);
            port.setFlowControl(flowControl);
            port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 1000, 0);

            boolean connected = port.openPort(2000); // Timeout de 2 segundos

            this.logService = log; // Initialize the member variable

            if (connected) {
                // Salva configurações atuais
                currentPortName = portName;
                currentBaudRate = baudRate;
                currentDataBits = dataBits;
                currentStopBits = stopBits;
                currentParity = parity;
                currentFlowControl = flowControl;
                
                String configSummary = String.format(
                    "Serial conectada: %s @ %d bps, %d-%s-%s, FC: %s",
                    portName, baudRate, dataBits, 
                    getParityString(parity), getStopBitsString(stopBits),
                    getFlowControlString(flowControl)
                );
                
                log.info(configSummary);
                notifyConnectionStatusChanged(true, portName);
                
                startReader(log);
                return true;
            } else {
                String errorMsg = "Falha ao abrir porta serial: " + portName;
                log.error(errorMsg);
                notifyError(errorMsg);
                return false;
            }
        } catch (Exception e) {
            String errorMsg = "Erro ao conectar serial " + portName + ": " + e.getMessage();
            log.error(errorMsg);
            notifyError(errorMsg);
            return false;
        }
    }

    /**
     * Reconecta com as últimas configurações usadas.
     */
    public boolean reconnect(LogService log) {
        if (currentPortName != null) {
            return connect(currentPortName, currentBaudRate, currentDataBits,
                         currentStopBits, currentParity, currentFlowControl, log);
        }
        return false;
    }

    /**
     * Inicia thread de leitura de dados com tratamento robusto.
     */
    private void startReader(LogService log) {
        isReading = true;
        readerThread = new Thread(() -> {
            this.logService.debug("Thread de leitura serial iniciada para: " + currentPortName);
            
            try (InputStream in = port.getInputStream()) {
                byte[] buffer = new byte[1024];
                
                while (isReading && !Thread.currentThread().isInterrupted()) {
                    try {
                        int len = in.read(buffer);
                        if (len > 0) {
                            String received = new String(buffer, 0, len, StandardCharsets.UTF_8);
                            totalBytesReceived.addAndGet(len);
                            
                            // Log e notificação
                            String logMessage = "Serial << " + received.replace("\r", "\\r").replace("\n", "\\n");
                            this.logService.debug(logMessage);
                            
                            // Notifica listener se existir
                            if (eventListener != null) {
                                eventListener.onDataReceived(received);
                            }
                        } else if (len == -1) {
                            // Fim do stream (conexão fechada)
                            break;
                        }
                        
                        // Pequena pausa para evitar uso excessivo de CPU
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        break;
                    } catch (Exception e) {
                        if (isReading) { // Só loga se ainda estiver lendo ativamente
                            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("timed out")) {
                                // Timeout de leitura é esperado, apenas continue.
                                this.logService.debug("Timeout na leitura serial, aguardando dados...");
                            } else {
                                // Outros erros de leitura são potencialmente fatais para a thread.
                                this.logService.error("Erro na leitura serial: " + e.getMessage());
                                notifyError("Erro na leitura: " + e.getMessage());
                                break;
                            }
                        } else {
                            // Se não estamos mais lendo, sair do loop.
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                this.logService.error("Erro no stream de leitura: " + e.getMessage());
                notifyError("Erro no stream: " + e.getMessage());
            } finally {
                this.logService.debug("Thread de leitura serial finalizada");
            }
        }, "SerialReader-" + currentPortName);
        
        readerThread.setDaemon(true);
        readerThread.start();
    }

    /**
     * Envia dados pela porta serial com múltiplas tentativas.
     */
    public boolean send(String data) {
        return send(data, 1); // 1 tentativa padrão
    }

    /**
     * Envia dados com número específico de tentativas.
     */
    public boolean send(String data, int maxAttempts) {
        if (!isConnected()) {
            notifyError("Tentativa de envio sem conexão serial");
            return false;
        }

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                OutputStream out = port.getOutputStream();
                byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
                
                this.logService.debug("Sending string data: " + data.replace("\r", "\\r").replace("\n", "\\n"));
                out.write(dataBytes);
                out.flush();
                
                // Atualiza estatísticas
                totalBytesSent.addAndGet(dataBytes.length);
                successfulSends.incrementAndGet();
                
                return true;
            } catch (Exception e) {
                if (attempt == maxAttempts) {
                    failedSends.incrementAndGet();
                    notifyError("Falha no envio serial (" + attempt + "/" + maxAttempts + "): " + e.getMessage());
                } else {
                    try {
                        Thread.sleep(100); // Pequena pausa entre tentativas
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Envia dados em bytes brutos.
     */
    public boolean send(byte[] data) {
        if (!isConnected()) {
            notifyError("Tentativa de envio sem conexão serial");
            return false;
        }

        try {
            OutputStream out = port.getOutputStream();
            
            StringBuilder hex = new StringBuilder();
            for (byte b : data) {
                hex.append(String.format("%02X ", b));
            }
            this.logService.debug("Sending byte data: " + hex.toString().trim());

            out.write(data);
            out.flush();
            
            totalBytesSent.addAndGet(data.length);
            successfulSends.incrementAndGet();
            
            return true;
        } catch (Exception e) {
            failedSends.incrementAndGet();
            notifyError("Falha no envio serial de bytes: " + e.getMessage());
            return false;
        }
    }

    /**
     * Envia comando com quebra de linha automática.
     */
    public boolean sendCommand(String command) {
        return send(command + "\r\n");
    }

    /**
     * Limpa buffers de entrada e saída.
     */
    public boolean clearBuffers() {
        if (isConnected()) {
            try {
                port.clearDTR();
                port.clearRTS();
                // Pequena pausa para que os sinais sejam processados
                Thread.sleep(50);
                port.setDTR();
                port.setRTS();
                return true;
            } catch (Exception e) {
                notifyError("Erro ao limpar buffers: " + e.getMessage());
                return false;
            }
        }
        return false;
    }

    /**
     * Verifica se a porta está aberta e comunicando.
     */
    public boolean isConnected() {
        return port != null && port.isOpen() && isReading;
    }

    /**
     * Obtém estatísticas de comunicação.
     */
    public SerialStats getStats() {
        return new SerialStats(
            totalBytesSent.get(),
            totalBytesReceived.get(),
            successfulSends.get(),
            failedSends.get(),
            currentPortName
        );
    }

    /**
     * Reinicia as estatísticas de comunicação.
     */
    public void resetStats() {
        totalBytesSent.set(0);
        totalBytesReceived.set(0);
        successfulSends.set(0);
        failedSends.set(0);
    }

    /**
     * Desconecta e libera recursos.
     */
    public void disconnect() {
        isReading = false;
        
        try {
            if (readerThread != null && readerThread.isAlive()) {
                readerThread.interrupt();
                readerThread.join(1000); // Espera até 1 segundo
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        try {
            if (port != null && port.isOpen()) {
                port.closePort();
                notifyConnectionStatusChanged(false, currentPortName);
            }
        } catch (Exception e) {
            // Ignorar erros no fechamento
        } finally {
            port = null;
            readerThread = null;
        }
    }

    /**
     * Define o listener para eventos seriais.
     */
    public void setEventListener(SerialEventListener listener) {
        this.eventListener = listener;
    }

    /**
     * Obtém a porta serial atual.
     */
    public SerialPort getPort() {
        return port;
    }

    /**
     * Obtém o nome da porta atual.
     */
    public String getCurrentPortName() {
        return currentPortName;
    }

    /**
     * Obtém a taxa de baud atual.
     */
    public int getCurrentBaudRate() {
        return currentBaudRate;
    }

    public PortStatus checkPortStatus(String portName, LogService log) {
        SerialPort testPort = null;
        try {
            testPort = SerialPort.getCommPort(portName);
            // A jSerialComm pode retornar um objeto mesmo para uma porta inválida, 
            // mas o nome do descritor será diferente ou vazio.
            // Uma verificação mais robusta é tentar abrir.
            if (!java.util.Arrays.stream(SerialPort.getCommPorts()).anyMatch(p -> p.getSystemPortName().equals(portName))) {
                this.logService.debug(String.format("PortStatus for %s: NOT_FOUND", portName));
                return PortStatus.NOT_FOUND;
            }

            if (testPort.openPort()) {
                testPort.closePort();
                this.logService.debug(String.format("PortStatus for %s: FREE", portName));
                return PortStatus.FREE;
            } else {
                this.logService.debug(String.format("PortStatus for %s: BUSY", portName));
                return PortStatus.BUSY;
            }
        } catch (Exception e) {
            // Isso pode acontecer se a porta for removida durante a verificação
            return PortStatus.ERROR;
        } finally {
            if (testPort != null && testPort.isOpen()) {
                testPort.closePort();
            }
        }
    }

    // ================== Métodos Auxiliares ==================
    
    private void notifyConnectionStatusChanged(boolean connected, String portName) {
        if (eventListener != null) {
            eventListener.onConnectionStatusChanged(connected, portName);
        }
    }
    
    private void notifyError(String errorMessage) {
        if (eventListener != null) {
            eventListener.onError(errorMessage);
        }
    }
    
    private String getParityString(int parity) {
        switch (parity) {
            case SerialPort.NO_PARITY: return "N";
            case SerialPort.ODD_PARITY: return "O";
            case SerialPort.EVEN_PARITY: return "E";
            case SerialPort.MARK_PARITY: return "M";
            case SerialPort.SPACE_PARITY: return "S";
            default: return "?";
        }
    }
    
    private String getStopBitsString(int stopBits) {
        switch (stopBits) {
            case SerialPort.ONE_STOP_BIT: return "1";
            case SerialPort.ONE_POINT_FIVE_STOP_BITS: return "1.5";
            case SerialPort.TWO_STOP_BITS: return "2";
            default: return "?";
        }
    }
    
    private String getFlowControlString(int flowControl) {
        switch (flowControl) {
            case SerialPort.FLOW_CONTROL_DISABLED: return "None";
            case SerialPort.FLOW_CONTROL_RTS_ENABLED: return "RTS";
            case SerialPort.FLOW_CONTROL_CTS_ENABLED: return "CTS";
            case SerialPort.FLOW_CONTROL_DSR_ENABLED: return "DSR";
            case SerialPort.FLOW_CONTROL_DTR_ENABLED: return "DTR";
            case SerialPort.FLOW_CONTROL_XONXOFF_IN_ENABLED: return "XON/XOFF In";
            case SerialPort.FLOW_CONTROL_XONXOFF_OUT_ENABLED: return "XON/XOFF Out";
            default: return "Unknown";
        }
    }

    /**
     * Classe para estatísticas de comunicação serial.
     */
    public static class SerialStats {
        public final long bytesSent;
        public final long bytesReceived;
        public final int successfulSends;
        public final int failedSends;
        public final String portName;
        
        public SerialStats(long bytesSent, long bytesReceived, 
                          int successfulSends, int failedSends, String portName) {
            this.bytesSent = bytesSent;
            this.bytesReceived = bytesReceived;
            this.successfulSends = successfulSends;
            this.failedSends = failedSends;
            this.portName = portName;
        }
        
        public double getSuccessRate() {
            int total = successfulSends + failedSends;
            return total > 0 ? (successfulSends * 100.0) / total : 100.0;
        }
        
        @Override
        public String toString() {
            return String.format(
                "SerialStats{port=%s, sent=%d, received=%d, success=%.1f%%, errors=%d}",
                portName, bytesSent, bytesReceived, getSuccessRate(), failedSends
            );
        }
    }

    /**
     * Libera recursos ao ser garbage collected.
     */
    @Override
    protected void finalize() throws Throwable {
        try {
            disconnect();
        } finally {
            super.finalize();
        }
    }
}