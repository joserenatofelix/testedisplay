package com.example.testedisplay.service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Serviço de logging thread-safe.
 * Registra logs com timestamp, nível (INFO, ERROR, DEBUG, etc.) e mensagem.
 */
public class LogService {
    private final PrintWriter writer;
    private final String filename;
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Construtor: cria diretório de logs (se não existir)
     * e inicializa arquivo com timestamp.
     */
    public LogService() {
        String timestampFile = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        filename = "logs/logs_" + timestampFile + ".txt";

        PrintWriter tmpWriter = null;
        try {
            File dir = new File("logs");
            if (!dir.exists() && !dir.mkdirs()) {
                System.err.println("Falha ao criar diretório de logs.");
            }
            tmpWriter = new PrintWriter(new FileWriter(filename, true), true);
        } catch (IOException e) {
            System.err.println("Erro ao inicializar LogService: " + e.getMessage());
            e.printStackTrace();
        }
        writer = tmpWriter;
    }

    /**
     * Registra mensagem no log com nível especificado.
     * @param level Nível de log (ex: INFO, ERROR, DEBUG)
     * @param text Texto da mensagem
     */
    public synchronized void log(String level, String text) {
        if (writer != null) {
            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
            writer.printf("[%s][%s] %s%n", timestamp, level, text);
        }
    }

    /**
     * Atalho para log de nível INFO.
     */
    public void info(String text) {
        log("INFO", text);
    }

    /**
     * Atalho para log de nível ERROR.
     */
    public void error(String text) {
        log("ERROR", text);
    }

    /**
     * Atalho para log de nível DEBUG.
     */
    public void debug(String text) {
        log("DEBUG", text);
    }

    /**
     * Fecha o arquivo de log.
     */
    public void close() {
        if (writer != null) {
            writer.close();
        }
    }

    /**
     * Retorna o nome do arquivo de log criado.
     * @return nome do arquivo de log
     */
    public String getFilename() {
        return filename;
    }
}
