package com.example.testedisplay.service;

import com.fazecast.jSerialComm.SerialPort;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Serviço para comunicação serial.
 * Gerencia conexão, envio e leitura assíncrona de dados.
 */
public class SerialService {
    private SerialPort port;
    private Thread readerThread;

    /**
     * Conecta na porta serial e inicia thread de leitura.
     * @param portName nome da porta (ex: COM3)
     * @param baud baud rate (ex: 19200)
     * @param log LogService para registrar logs
     * @return true se conectou com sucesso
     */
    public boolean connect(String portName, int baud, LogService log) {
        try {
            port = SerialPort.getCommPort(portName);
            port.setComPortParameters(baud, 8, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY);
            port.setComPortTimeouts(SerialPort.TIMEOUT_NONBLOCKING, 0, 0);

            boolean ok = port.openPort();

            if (ok) {
                log.info("Serial conectada: " + portName + " @ " + baud + "bps");
                startReader(log);
            } else {
                log.error("Falha ao abrir porta serial: " + portName);
            }

            return ok;
        } catch (Exception e) {
            log.error("Erro ao conectar serial: " + e.getMessage());
            return false;
        }
    }

    /**
     * Inicia thread que fica lendo dados da porta e envia para o log como DEBUG.
     */
    private void startReader(LogService log) {
        readerThread = new Thread(() -> {
            try (InputStream in = port.getInputStream()) {
                byte[] buffer = new byte[1024];
                int len;
                while (!Thread.currentThread().isInterrupted() && (len = in.read(buffer)) > -1) {
                    String received = new String(buffer, 0, len, StandardCharsets.UTF_8);
                    log.debug("Serial << " + received.replace("\r", "\\r").replace("\n", "\\n"));
                }
            } catch (Exception e) {
                log.error("Erro leitura serial: " + e.getMessage());
            }
        }, "SerialReaderThread");
        readerThread.setDaemon(true); // Termina junto com a aplicação
        readerThread.start();
    }

    /**
     * Envia dados pela porta serial.
     * @param data string a ser enviada
     * @throws Exception se não estiver conectado ou falhar envio
     */
    public void send(String data) throws Exception {
        if (isConnected()) {
            port.getOutputStream().write(data.getBytes(StandardCharsets.UTF_8));
            port.getOutputStream().flush();
        } else {
            throw new IllegalStateException("Serial não conectada.");
        }
    }

    /**
     * Verifica se a porta está aberta.
     * @return true se aberta
     */
    public boolean isConnected() {
        return port != null && port.isOpen();
    }

    /**
     * Desconecta e encerra thread de leitura.
     */
    public void disconnect() {
        try {
            if (readerThread != null && readerThread.isAlive()) {
                readerThread.interrupt();
            }
            if (port != null && port.isOpen()) {
                port.closePort();
            }
        } catch (Exception e) {
            // Ignorar erros ao fechar (não afeta aplicação)
        }
    }
}
