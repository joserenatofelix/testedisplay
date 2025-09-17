package com.example.testedisplay.service;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;

/**
 * Serviço avançado para comunicação TCP com reconexão automática,
 * timeout configurável, SSL/TLS suporte e estatísticas detalhadas.
 */
public class TcpService {
    private Socket socket;
    private Thread readerThread;
    private volatile boolean isReading = false;
    
    // Estatísticas
    private final AtomicLong totalBytesSent = new AtomicLong(0);
    private final AtomicLong totalBytesReceived = new AtomicLong(0);
    private final AtomicInteger successfulSends = new AtomicInteger(0);
    private final AtomicInteger failedSends = new AtomicInteger(0);
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    
    // Configurações
    private String currentIp;
    private int currentPort;
    private int connectTimeout = 5000; // 5 segundos
    private int readTimeout = 30000;   // 30 segundos
    private boolean useSsl = false;
    private boolean autoReconnect = false;
    
    // Callbacks para eventos
    private TcpEventListener eventListener;
    
    // Interface para eventos TCP
    public interface TcpEventListener {
        void onDataReceived(String data);
        void onConnectionStatusChanged(boolean connected, String address);
        void onError(String errorMessage);
        void onReconnecting(int attempt, int maxAttempts);
    }

    /**
     * Conecta ao servidor TCP com configurações padrão.
     */
    public boolean connect(String ip, int port, LogService log) {
        return connect(ip, port, false, 5000, 30000, log);
    }

    /**
     * Conecta ao servidor TCP com configurações completas.
     */
    public boolean connect(String ip, int port, boolean useSsl, 
                         int connectTimeoutMs, int readTimeoutMs, LogService log) {
        try {
            disconnect(); // Garante desconexão prévia
            
            this.currentIp = ip;
            this.currentPort = port;
            this.useSsl = useSsl;
            this.connectTimeout = connectTimeoutMs;
            this.readTimeout = readTimeoutMs;

            if (useSsl) {
                SSLSocketFactory factory = (SSLSocketFactory) SSLSocketFactory.getDefault();
                socket = factory.createSocket();
            } else {
                socket = new Socket();
            }
            
            // Configura timeouts
            socket.setSoTimeout(readTimeoutMs);
            
            // Conecta com timeout
            socket.connect(new InetSocketAddress(ip, port), connectTimeoutMs);
            
            if (useSsl && socket instanceof SSLSocket) {
                ((SSLSocket) socket).startHandshake();
            }
            
            String protocol = useSsl ? "SSL/TCP" : "TCP";
            log.info(protocol + " conectado: " + ip + ":" + port);
            notifyConnectionStatusChanged(true, ip + ":" + port);
            
            startReader(log);
            return true;
            
        } catch (Exception e) {
            String errorMsg = "Erro ao conectar " + (useSsl ? "SSL/" : "") + 
                            "TCP " + ip + ":" + port + ": " + e.getMessage();
            log.error(errorMsg);
            notifyError(errorMsg);
            return false;
        }
    }

    /**
     * Reconecta com as últimas configurações usadas.
     */
    public boolean reconnect(LogService log) {
        if (currentIp != null) {
            notifyReconnecting(reconnectAttempts.get() + 1, 3);
            return connect(currentIp, currentPort, useSsl, connectTimeout, readTimeout, log);
        }
        return false;
    }

    /**
     * Inicia thread de leitura de dados com tratamento robusto.
     */
    private void startReader(LogService log) {
        isReading = true;
        readerThread = new Thread(() -> {
            log.debug("Thread de leitura TCP iniciada para: " + currentIp + ":" + currentPort);
            
            try (InputStream in = socket.getInputStream()) {
                byte[] buffer = new byte[4096]; // Buffer maior para TCP
                
                while (isReading && !Thread.currentThread().isInterrupted()) {
                    try {
                        int len = in.read(buffer);
                        if (len > 0) {
                            String received = new String(buffer, 0, len, StandardCharsets.UTF_8);
                            totalBytesReceived.addAndGet(len);
                            
                            // Log e notificação
                            String logMessage = "TCP << " + received.replace("\r", "\\r").replace("\n", "\\n");
                            log.debug(logMessage);
                            
                            // Notifica listener se existir
                            if (eventListener != null) {
                                eventListener.onDataReceived(received);
                            }
                        } else if (len == -1) {
                            // Fim do stream (conexão fechada pelo servidor)
                            log.info("Conexão TCP fechada pelo servidor");
                            break;
                        }
                    } catch (java.net.SocketTimeoutException e) {
                        // Timeout de leitura é normal, continua aguardando
                        continue;
                    } catch (Exception e) {
                        if (isReading) { // Só loga se ainda estiver lendo ativamente
                            String errorMsg = "Erro na leitura TCP: " + e.getMessage();
                            log.error(errorMsg);
                            notifyError(errorMsg);
                        }
                        break;
                    }
                }
            } catch (Exception e) {
                log.error("Erro no stream de leitura TCP: " + e.getMessage());
                notifyError("Erro no stream: " + e.getMessage());
            } finally {
                log.debug("Thread de leitura TCP finalizada");
                isReading = false;
                
                // Se autoReconnect está habilitado, tenta reconectar
                if (autoReconnect && !Thread.currentThread().isInterrupted()) {
                    attemptAutoReconnect(log);
                }
            }
        }, "TcpReader-" + currentIp + ":" + currentPort);
        
        readerThread.setDaemon(true);
        readerThread.start();
    }

    /**
     * Tenta reconexão automática em caso de queda de conexão.
     */
    private void attemptAutoReconnect(LogService log) {
        final int maxAttempts = 5;
        final long initialDelay = 1000; // 1 segundo
        final long maxDelay = 30000;    // 30 segundos
        
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                long delay = Math.min(initialDelay * (long) Math.pow(2, attempt - 1), maxDelay);
                log.info("Tentativa de reconexão " + attempt + "/" + maxAttempts + " em " + delay + "ms");
                
                notifyReconnecting(attempt, maxAttempts);
                Thread.sleep(delay);
                
                if (reconnect(log)) {
                    log.info("Reconexão TCP bem-sucedida");
                    reconnectAttempts.set(0);
                    return;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Erro na tentativa de reconexão " + attempt + ": " + e.getMessage());
            }
        }
        
        log.error("Falha em todas as tentativas de reconexão TCP");
        notifyConnectionStatusChanged(false, currentIp + ":" + currentPort);
    }

    /**
     * Envia dados ao servidor TCP com múltiplas tentativas.
     */
    public boolean send(String data) {
        return send(data, 1); // 1 tentativa padrão
    }

    /**
     * Envia dados com número específico de tentativas.
     */
    public boolean send(String data, int maxAttempts) {
        if (!isConnected()) {
            notifyError("Tentativa de envio sem conexão TCP ativa");
            return false;
        }

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                OutputStream out = socket.getOutputStream();
                byte[] dataBytes = data.getBytes(StandardCharsets.UTF_8);
                
                out.write(dataBytes);
                out.flush();
                
                // Atualiza estatísticas
                totalBytesSent.addAndGet(dataBytes.length);
                successfulSends.incrementAndGet();
                
                return true;
            } catch (Exception e) {
                if (attempt == maxAttempts) {
                    failedSends.incrementAndGet();
                    notifyError("Falha no envio TCP (" + attempt + "/" + maxAttempts + "): " + e.getMessage());
                    
                    // Verifica se a conexão caiu durante o envio
                    if (!isConnected()) {
                        notifyConnectionStatusChanged(false, currentIp + ":" + currentPort);
                    }
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
            notifyError("Tentativa de envio sem conexão TCP ativa");
            return false;
        }

        try {
            OutputStream out = socket.getOutputStream();
            out.write(data);
            out.flush();

            totalBytesSent.addAndGet(data.length);
            successfulSends.incrementAndGet();

            return true;
        } catch (Exception e) {
            failedSends.incrementAndGet();
            notifyError("Falha no envio TCP de bytes: " + e.getMessage());
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
     * Verifica se a conexão TCP está ativa e comunicando.
     */
    public boolean isConnected() {
        return socket != null && 
               socket.isConnected() && 
               !socket.isClosed() && 
               isReading;
    }

    /**
     * Obtém estatísticas de comunicação.
     */
    public TcpStats getStats() {
        return new TcpStats(
            totalBytesSent.get(),
            totalBytesReceived.get(),
            successfulSends.get(),
            failedSends.get(),
            reconnectAttempts.get(),
            currentIp + ":" + currentPort
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
        reconnectAttempts.set(0);
    }

    /**
     * Habilita/desabilita reconexão automática.
     */
    public void setAutoReconnect(boolean enabled) {
        this.autoReconnect = enabled;
    }

    /**
     * Define timeouts de conexão e leitura.
     */
    public void setTimeouts(int connectTimeoutMs, int readTimeoutMs) {
        this.connectTimeout = connectTimeoutMs;
        this.readTimeout = readTimeoutMs;
        
        if (socket != null && !socket.isClosed()) {
            try {
                socket.setSoTimeout(readTimeoutMs);
            } catch (Exception e) {
                notifyError("Erro ao configurar timeout: " + e.getMessage());
            }
        }
    }

    /**
     * Desconecta e libera recursos.
     */
    public void disconnect() {
        isReading = false;
        autoReconnect = false; // Desabilita auto-reconexão ao desconectar manualmente
        
        try {
            if (readerThread != null && readerThread.isAlive()) {
                readerThread.interrupt();
                readerThread.join(1000); // Espera até 1 segundo
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        try {
            if (socket != null && !socket.isClosed()) {
                socket.close();
                notifyConnectionStatusChanged(false, currentIp + ":" + currentPort);
            }
        } catch (Exception e) {
            // Ignorar erros no fechamento
        } finally {
            socket = null;
            readerThread = null;
        }
    }

    /**
     * Define o listener para eventos TCP.
     */
    public void setEventListener(TcpEventListener listener) {
        this.eventListener = listener;
    }

    /**
     * Obtém o endereço atual da conexão.
     */
    public String getCurrentAddress() {
        return currentIp + ":" + currentPort;
    }

    /**
     * Verifica se SSL está habilitado.
     */
    public boolean isSslEnabled() {
        return useSsl;
    }

    // ================== Métodos Auxiliares ==================
    
    private void notifyConnectionStatusChanged(boolean connected, String address) {
        if (eventListener != null) {
            eventListener.onConnectionStatusChanged(connected, address);
        }
    }
    
    private void notifyError(String errorMessage) {
        if (eventListener != null) {
            eventListener.onError(errorMessage);
        }
    }
    
    private void notifyReconnecting(int attempt, int maxAttempts) {
        if (eventListener != null) {
            eventListener.onReconnecting(attempt, maxAttempts);
        }
    }

    /**
     * Classe para estatísticas de comunicação TCP.
     */
    public static class TcpStats {
        public final long bytesSent;
        public final long bytesReceived;
        public final int successfulSends;
        public final int failedSends;
        public final int reconnectAttempts;
        public final String address;
        
        public TcpStats(long bytesSent, long bytesReceived, 
                       int successfulSends, int failedSends, 
                       int reconnectAttempts, String address) {
            this.bytesSent = bytesSent;
            this.bytesReceived = bytesReceived;
            this.successfulSends = successfulSends;
            this.failedSends = failedSends;
            this.reconnectAttempts = reconnectAttempts;
            this.address = address;
        }
        
        public double getSuccessRate() {
            int total = successfulSends + failedSends;
            return total > 0 ? (successfulSends * 100.0) / total : 100.0;
        }
        
        @Override
        public String toString() {
            return String.format(
                "TcpStats{address=%s, sent=%d, received=%d, success=%.1f%%, errors=%d, reconnects=%d}",
                address, bytesSent, bytesReceived, getSuccessRate(), failedSends, reconnectAttempts
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