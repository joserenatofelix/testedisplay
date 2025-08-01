package com.example.testedisplay.service;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Serviço para comunicação TCP.
 * Gerencia conexão, envio e leitura assíncrona de dados.
 */
public class TcpService {
    private Socket socket;
    private Thread readerThread;

    /**
     * Conecta ao servidor TCP e inicia leitura assíncrona.
     * @param ip endereço IP do servidor
     * @param port porta do servidor
     * @param log LogService para registrar logs
     * @return true se conectado com sucesso
     */
    public boolean connect(String ip, int port, LogService log) {
        try {
            socket = new Socket(ip, port);
            log.info("TCP conectado: " + ip + ":" + port);
            startReader(log);
            return true;
        } catch (Exception e) {
            log.error("Erro ao conectar TCP: " + e.getMessage());
            return false;
        }
    }

    /**
     * Inicia thread que lê dados recebidos e envia para log como DEBUG.
     */
    private void startReader(LogService log) {
        readerThread = new Thread(() -> {
            try (InputStream in = socket.getInputStream()) {
                byte[] buffer = new byte[1024];
                int len;
                while (!Thread.currentThread().isInterrupted() && (len = in.read(buffer)) > -1) {
                    String received = new String(buffer, 0, len, StandardCharsets.UTF_8);
                    log.debug("TCP << " + received.replace("\r", "\\r").replace("\n", "\\n"));
                }
            } catch (Exception e) {
                log.error("Erro leitura TCP: " + e.getMessage());
            }
        }, "TcpReaderThread");
        readerThread.setDaemon(true); // Encerra junto com o app
        readerThread.start();
    }

    /**
     * Envia dados ao servidor TCP.
     * @param data string a ser enviada
     * @throws Exception se não estiver conectado ou falhar envio
     */
    public void send(String data) throws Exception {
        if (isConnected()) {
            OutputStream out = socket.getOutputStream();
            out.write(data.getBytes(StandardCharsets.UTF_8));
            out.flush();
        } else {
            throw new IllegalStateException("TCP não está conectado.");
        }
    }

    /**
     * Verifica se o socket está conectado e não fechado.
     * @return true se conectado
     */
    public boolean isConnected() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    /**
     * Encerra conexão e thread de leitura.
     */
    public void disconnect() {
        try {
            if (readerThread != null && readerThread.isAlive()) {
                readerThread.interrupt();
            }
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        } catch (Exception e) {
            // Ignorar erros ao fechar (não afeta funcionamento)
        }
    }
}
