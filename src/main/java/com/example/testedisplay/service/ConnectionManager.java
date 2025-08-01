package com.example.testedisplay.service;

/**
 * Gerencia a conexão atual (Serial ou TCP) de forma unificada.
 */
public class ConnectionManager {
    private final SerialService serialService;
    private final TcpService tcpService;
    private final LogService logService;

    public ConnectionManager(SerialService serialService, TcpService tcpService, LogService logService) {
        this.serialService = serialService;
        this.tcpService = tcpService;
        this.logService = logService;
    }

    /**
     * Envia dado pela conexão ativa (Serial ou TCP).
     */
    public void send(String data) throws Exception {
        if (serialService.isConnected()) {
            serialService.send(data);
            logService.debug("Enviado pela Serial: " + data.replace("\r", "\\r").replace("\n", "\\n"));
        } else if (tcpService.isConnected()) {
            tcpService.send(data);
            logService.debug("Enviado via TCP: " + data.replace("\r", "\\r").replace("\n", "\\n"));
        } else {
            logService.error("Tentativa de enviar sem conexão ativa.");
            throw new IllegalStateException("Nenhuma conexão ativa.");
        }
    }

    /**
     * Verifica se há alguma conexão ativa.
     */
    public boolean isConnected() {
        return serialService.isConnected() || tcpService.isConnected();
    }

    /**
     * Desconecta ambas as conexões.
     */
    public void disconnectAll() {
        serialService.disconnect();
        tcpService.disconnect();
        logService.info("Todas as conexões foram desconectadas.");
    }
}
