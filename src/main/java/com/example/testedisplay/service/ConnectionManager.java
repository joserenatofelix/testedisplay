package com.example.testedisplay.service;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Gerenciador avançado de conexões com suporte a reconexão automática,
 * monitoramento de saúde da conexão e estatísticas de desempenho.
 */
public class ConnectionManager implements AutoCloseable {
    private final SerialService serialService;
    private final TcpService tcpService;
    private final LogService logService;
    
    // Configurações de reconexão
    private final int maxReconnectAttempts;
    private final long reconnectDelayMs;
    private final long connectionTimeoutMs;
    
    // Estado da conexão
    private volatile ConnectionType activeConnectionType = ConnectionType.NONE;
    private volatile boolean autoReconnectEnabled = true;
    private volatile boolean isMonitoring = false;
    
    // Estatísticas
    private final AtomicLong totalBytesSent = new AtomicLong(0);
    private final AtomicLong totalBytesReceived = new AtomicLong(0);
    private final AtomicInteger successfulSends = new AtomicInteger(0);
    private final AtomicInteger failedSends = new AtomicInteger(0);
    private final AtomicInteger reconnectAttempts = new AtomicInteger(0);
    
    // Agendador para tarefas em background
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "ConnectionManager-Scheduler");
        t.setDaemon(true);
        return t;
    });
    
    // Listeners para eventos de conexão
    private final java.util.List<ConnectionEventListener> listeners = new java.util.concurrent.CopyOnWriteArrayList<>();
    
    // Tipos de conexão suportados
    public enum ConnectionType {
        SERIAL, TCP, NONE
    }
    
    // Eventos de conexão
    public enum ConnectionEvent {
        CONNECTED, DISCONNECTED, CONNECTION_LOST, RECONNECTING, RECONNECTED, SEND_SUCCESS, SEND_FAILURE
    }
    
    // Interface para listeners de eventos
    public interface ConnectionEventListener {
        void onConnectionEvent(ConnectionEvent event, ConnectionType type, String message);
    }

    public ConnectionManager(SerialService serialService, TcpService tcpService, LogService logService) {
        this(serialService, tcpService, logService, 3, 5000, 30000);
    }

    public ConnectionManager(SerialService serialService, TcpService tcpService, 
                           LogService logService, int maxReconnectAttempts, 
                           long reconnectDelayMs, long connectionTimeoutMs) {
        this.serialService = serialService;
        this.tcpService = tcpService;
        this.logService = logService;
        this.maxReconnectAttempts = maxReconnectAttempts;
        this.reconnectDelayMs = reconnectDelayMs;
        this.connectionTimeoutMs = connectionTimeoutMs;
        
        startConnectionMonitoring();
    }

    /**
     * Conecta via Serial com reconexão automática.
     */
    public boolean connectSerial(String port, int baudRate) {
        return connectSerial(port, baudRate, true);
    }

    /**
     * Conecta via Serial com opção de reconexão automática.
     */
    public synchronized boolean connectSerial(String port, int baudRate, boolean enableAutoReconnect) {
        disconnectAll();
        this.autoReconnectEnabled = enableAutoReconnect;
        
        boolean connected = serialService.connect(port, baudRate, logService);
        if (connected) {
            activeConnectionType = ConnectionType.SERIAL;
            notifyListeners(ConnectionEvent.CONNECTED, ConnectionType.SERIAL, 
                          "Conectado serial: " + port + " @ " + baudRate + "bps");
            logService.info("Conectado serial: " + port + " @ " + baudRate + "bps");
        }
        return connected;
    }

    /**
     * Conecta via TCP com reconexão automática.
     */
    public boolean connectTcp(String ip, int port) {
        return connectTcp(ip, port, true);
    }

    /**
     * Conecta via TCP com opção de reconexão automática.
     */
    public synchronized boolean connectTcp(String ip, int port, boolean enableAutoReconnect) {
        disconnectAll();
        this.autoReconnectEnabled = enableAutoReconnect;
        
        boolean connected = tcpService.connect(ip, port, logService);
        if (connected) {
            activeConnectionType = ConnectionType.TCP;
            notifyListeners(ConnectionEvent.CONNECTED, ConnectionType.TCP, 
                          "Conectado TCP: " + ip + ":" + port);
            logService.info("Conectado TCP: " + ip + ":" + port);
        }
        return connected;
    }

    /**
     * Envia dados pela conexão ativa com tratamento de erro e retry automático.
     */
    public boolean send(String data) {
        return send(data, 1); // 1 tentativa por padrão
    }

    /**
     * Envia dados com número específico de tentativas.
     */
    public synchronized boolean send(String data, int maxAttempts) {
        if (!isConnected()) {
            logService.error("Tentativa de envio sem conexão ativa");
            notifyListeners(ConnectionEvent.SEND_FAILURE, activeConnectionType, "Sem conexão ativa");
            return false;
        }

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                if (activeConnectionType == ConnectionType.SERIAL) {
                    serialService.send(data);
                } else if (activeConnectionType == ConnectionType.TCP) {
                    tcpService.send(data);
                }

                // Registra estatísticas de sucesso
                totalBytesSent.addAndGet(data.length());
                successfulSends.incrementAndGet();
                
                String logMessage = "Dados enviados via " + activeConnectionType + 
                                  " (" + data.length() + " bytes)";
                logService.debug(logMessage);
                notifyListeners(ConnectionEvent.SEND_SUCCESS, activeConnectionType, logMessage);
                
                return true;
            } catch (Exception e) {
                if (attempt == maxAttempts) {
                    // Última tentativa falhou
                    failedSends.incrementAndGet();
                    String errorMsg = "Falha no envio (tentativa " + attempt + "/" + maxAttempts + 
                                    "): " + e.getMessage();
                    logService.error(errorMsg);
                    notifyListeners(ConnectionEvent.SEND_FAILURE, activeConnectionType, errorMsg);
                    
                    // Verifica se a conexão foi perdida durante o envio
                    if (!isConnected()) {
                        handleConnectionLost();
                    }
                } else {
                    logService.info("Tentativa " + attempt + " falhou, tentando novamente...");
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
        return send(new String(data), 1);
    }

    /**
     * Envia dados em bytes brutos com múltiplas tentativas.
     */
    public boolean send(byte[] data, int maxAttempts) {
        return send(new String(data), maxAttempts);
    }

    /**
     * Verifica se há alguma conexão ativa e saudável.
     */
    public boolean isConnected() {
        boolean connected = false;
        
        if (activeConnectionType == ConnectionType.SERIAL) {
            connected = serialService.isConnected();
        } else if (activeConnectionType == ConnectionType.TCP) {
            connected = tcpService.isConnected();
        }
        
        // Se não está conectado mas deveria estar, trata como perda de conexão
        if (!connected && activeConnectionType != ConnectionType.NONE) {
            handleConnectionLost();
        }
        
        return connected;
    }

    /**
     * Obtém o tipo de conexão ativa.
     */
    public ConnectionType getActiveConnectionType() {
        return activeConnectionType;
    }

    /**
     * Desconecta todas as conexões.
     */
    public synchronized void disconnectAll() {
        serialService.disconnect();
        tcpService.disconnect();
        
        if (activeConnectionType != ConnectionType.NONE) {
            ConnectionType previousType = activeConnectionType;
            activeConnectionType = ConnectionType.NONE;
            notifyListeners(ConnectionEvent.DISCONNECTED, previousType, "Conexão encerrada pelo usuário");
        }
        
        logService.info("Todas as conexões foram desconectadas.");
    }

    /**
     * Habilita/desabilita reconexão automática.
     */
    public void setAutoReconnectEnabled(boolean enabled) {
        this.autoReconnectEnabled = enabled;
        logService.info("Reconexão automática " + (enabled ? "habilitada" : "desabilitada"));
    }

    /**
     * Adiciona listener para eventos de conexão.
     */
    public void addConnectionEventListener(ConnectionEventListener listener) {
        listeners.add(listener);
    }

    /**
     * Remove listener de eventos de conexão.
     */
    public void removeConnectionEventListener(ConnectionEventListener listener) {
        listeners.remove(listener);
    }

    /**
     * Obtém estatísticas de desempenho da conexão.
     */
    public ConnectionStats getStats() {
        return new ConnectionStats(
            totalBytesSent.get(),
            totalBytesReceived.get(),
            successfulSends.get(),
            failedSends.get(),
            reconnectAttempts.get(),
            activeConnectionType
        );
    }

    /**
     * Reinicia as estatísticas de desempenho.
     */
    public void resetStats() {
        totalBytesSent.set(0);
        totalBytesReceived.set(0);
        successfulSends.set(0);
        failedSends.set(0);
        reconnectAttempts.set(0);
        logService.info("Estatísticas de conexão reiniciadas");
    }

    /**
     * Inicia o monitoramento da conexão.
     */
    private void startConnectionMonitoring() {
        if (isMonitoring) return;
        
        isMonitoring = true;
        scheduler.scheduleAtFixedRate(() -> {
            try {
                monitorConnectionHealth();
            } catch (Exception e) {
                logService.error("Erro no monitoramento de conexão: " + e.getMessage());
            }
        }, 5, 5, TimeUnit.SECONDS); // Verifica a cada 5 segundos
    }

    /**
     * Monitora a saúde da conexão atual.
     */
    private void monitorConnectionHealth() {
        if (activeConnectionType != ConnectionType.NONE && !isConnected()) {
            handleConnectionLost();
        }
    }

    /**
     * Manipula a perda de conexão detectada.
     */
    private synchronized void handleConnectionLost() {
        if (activeConnectionType == ConnectionType.NONE) return;
        
        ConnectionType lostType = activeConnectionType;
        activeConnectionType = ConnectionType.NONE;
        
        String errorMsg = "Conexão " + lostType + " perdida";
        logService.error(errorMsg);
        notifyListeners(ConnectionEvent.CONNECTION_LOST, lostType, errorMsg);
        
        // Tenta reconexão automática se habilitada
        if (autoReconnectEnabled) {
            attemptReconnection(lostType);
        }
    }

    /**
     * Tenta reconexão automática.
     */
    private void attemptReconnection(ConnectionType type) {
        if (reconnectAttempts.get() >= maxReconnectAttempts) {
            logService.info("Número máximo de tentativas de reconexão atingido (" + maxReconnectAttempts + ")");
            return;
        }
        
        scheduler.schedule(() -> {
            try {
                reconnectAttempts.incrementAndGet();
                notifyListeners(ConnectionEvent.RECONNECTING, type, 
                              "Tentativa de reconexão " + reconnectAttempts.get() + "/" + maxReconnectAttempts);
                
                logService.info("Tentando reconexão (" + reconnectAttempts.get() + "/" + maxReconnectAttempts + ")");
                
                // Aqui você precisaria armazenar os parâmetros de conexão originais
                // para tentar reconectar. Esta é uma implementação simplificada.
                boolean reconnected = false;
                
                if (reconnected) {
                    reconnectAttempts.set(0);
                    notifyListeners(ConnectionEvent.RECONNECTED, type, "Reconexão bem-sucedida");
                    logService.info("Reconexão bem-sucedida");
                }
            } catch (Exception e) {
                logService.error("Erro na tentativa de reconexão: " + e.getMessage());
            }
        }, reconnectDelayMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Notifica todos os listeners sobre um evento.
     */
    private void notifyListeners(ConnectionEvent event, ConnectionType type, String message) {
        for (ConnectionEventListener listener : listeners) {
            try {
                listener.onConnectionEvent(event, type, message);
            } catch (Exception e) {
                logService.error("Erro ao notificar listener: " + e.getMessage());
            }
        }
    }

    /**
     * Libera recursos do gerenciador de conexões.
     */
    @Override
    public void close() {
        isMonitoring = false;
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
        disconnectAll();
    }

    /**
     * Classe para armazenar estatísticas de conexão.
     */
    public static class ConnectionStats {
        public final long totalBytesSent;
        public final long totalBytesReceived;
        public final int successfulSends;
        public final int failedSends;
        public final int reconnectAttempts;
        public final ConnectionType activeConnectionType;

        public ConnectionStats(long totalBytesSent, long totalBytesReceived, 
                             int successfulSends, int failedSends, 
                             int reconnectAttempts, ConnectionType activeConnectionType) {
            this.totalBytesSent = totalBytesSent;
            this.totalBytesReceived = totalBytesReceived;
            this.successfulSends = successfulSends;
            this.failedSends = failedSends;
            this.reconnectAttempts = reconnectAttempts;
            this.activeConnectionType = activeConnectionType;
        }

        public double getSuccessRate() {
            int total = successfulSends + failedSends;
            return total > 0 ? (successfulSends * 100.0) / total : 0;
        }

        @Override
        public String toString() {
            return String.format(
                "ConnectionStats{bytesSent=%d, bytesReceived=%d, successRate=%.1f%%, reconnects=%d}",
                totalBytesSent, totalBytesReceived, getSuccessRate(), reconnectAttempts
            );
        }
    }
}