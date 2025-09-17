package com.example.testedisplay.service;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Deque;

/**
 * Serviço de logging thread-safe, completo.
 * Suporta níveis de log, rotação de arquivos, JSON opcional, buffer em memória e eventos comuns.
 * Os logs são salvos na área de trabalho do usuário.
 */
public class LogService implements AutoCloseable {

    private PrintWriter writer;
    private String filename;
    private LogLevel minLevel = LogLevel.DEBUG;
    private boolean useJson = false;

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024; // 5 MB
    private final Deque<String> logBuffer;
    private final int bufferSize;

    public enum LogLevel {
        DEBUG, INFO, ERROR
    }

    // ================== Construtor ==================
    public LogService() {
        this(false, 100); // Default: JSON desabilitado, buffer com 100 registros
    }

    public LogService(boolean useJson, int bufferSize) {
        this.useJson = useJson;
        this.bufferSize = bufferSize > 0 ? bufferSize : 100;
        this.logBuffer = new ArrayDeque<>(this.bufferSize);

        // Obtém o caminho da área de trabalho
        String desktopPath = getDesktopPath();
        String timestampFile = LocalDateTime.now()
                .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        
        // Cria o diretório de logs na área de trabalho se não existir
        File logsDir = new File(desktopPath, "testedisplay_logs");
        if (!logsDir.exists() && !logsDir.mkdirs()) {
            System.err.println("Falha ao criar diretório de logs na área de trabalho.");
            // Fallback: usa o diretório atual
            logsDir = new File("testedisplay_logs");
            logsDir.mkdirs();
        }
        
        filename = new File(logsDir, "logs_" + timestampFile + ".txt").getAbsolutePath();

        try {
            writer = new PrintWriter(new FileWriter(filename, true), true);
            info("LogService inicializado. Arquivo: " + filename);
        } catch (IOException e) {
            System.err.println("Erro ao inicializar LogService: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Obtém o caminho da área de trabalho do usuário.
     */
    private String getDesktopPath() {
        String os = System.getProperty("os.name").toLowerCase();
        String desktopPath;
        
        if (os.contains("win")) {
            // Windows
            desktopPath = System.getProperty("user.home") + File.separator + "Desktop";
        } else if (os.contains("mac")) {
            // macOS
            desktopPath = System.getProperty("user.home") + File.separator + "Desktop";
        } else {
            // Linux e outros Unix-like
            desktopPath = System.getProperty("user.home") + File.separator + "Desktop";
            // Se não existir Desktop, usa o home directory
            File desktopDir = new File(desktopPath);
            if (!desktopDir.exists()) {
                desktopPath = System.getProperty("user.home");
            }
        }
        
        return desktopPath;
    }

    /**
     * Obtém o caminho completo do diretório de logs.
     */
    public String getLogsDirectoryPath() {
        return new File(filename).getParent();
    }

    /**
     * Abre o diretório de logs no explorador de arquivos do sistema.
     */
    public void openLogsDirectory() {
        try {
            java.awt.Desktop.getDesktop().open(new File(getLogsDirectoryPath()));
        } catch (Exception e) {
            error("Erro ao abrir diretório de logs: " + e.getMessage());
        }
    }

    /**
     * Obtém a lista de arquivos de log no diretório.
     */
    public List<String> getLogFiles() {
        List<String> logFiles = new ArrayList<>();
        File logsDir = new File(getLogsDirectoryPath());
        
        if (logsDir.exists() && logsDir.isDirectory()) {
            File[] files = logsDir.listFiles((dir, name) -> name.startsWith("logs_") && name.endsWith(".txt"));
            if (files != null) {
                for (File file : files) {
                    logFiles.add(file.getName());
                }
            }
        }
        
        return logFiles;
    }

    /**
     * Limpa arquivos de log antigos, mantendo apenas os últimos N arquivos.
     */
    public void cleanupOldLogs(int keepLastNFiles) {
        File logsDir = new File(getLogsDirectoryPath());
        
        if (logsDir.exists() && logsDir.isDirectory()) {
            File[] logFiles = logsDir.listFiles((dir, name) -> name.startsWith("logs_") && name.endsWith(".txt"));
            
            if (logFiles != null && logFiles.length > keepLastNFiles) {
                // Ordena por data de modificação (mais antigo primeiro)
                java.util.Arrays.sort(logFiles, java.util.Comparator.comparingLong(File::lastModified));
                
                for (int i = 0; i < logFiles.length - keepLastNFiles; i++) {
                    if (logFiles[i].delete()) {
                        debug("Arquivo de log antigo removido: " + logFiles[i].getName());
                    }
                }
            }
        }
    }

    // ================== Configurações ==================
    public void setMinLogLevel(LogLevel level) {
        this.minLevel = level;
        info("Nível mínimo de log alterado para: " + level);
    }

    public void setUseJson(boolean useJson) {
        this.useJson = useJson;
        info("Formato JSON " + (useJson ? "habilitado" : "desabilitado"));
    }

    public String getFilename() {
        return filename;
    }

    // ================== Log básico ==================
    public synchronized void log(LogLevel level, String text) {
        if (writer == null || level.ordinal() < minLevel.ordinal()) return;

        checkRotate();

        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String threadName = Thread.currentThread().getName();
        String logLine;

        if (useJson) {
            logLine = String.format("{\"timestamp\":\"%s\",\"thread\":\"%s\",\"level\":\"%s\",\"message\":\"%s\"}",
                    timestamp, threadName, level, escapeJson(text));
        } else {
            logLine = String.format("[%s][%s][%s] %s", timestamp, threadName, level, text);
        }

        writer.println(logLine);
        addToBuffer(logLine);
        
        // Também exibe no console para debugging
        if (level == LogLevel.ERROR) {
            System.err.println(logLine);
        } else {
            System.out.println(logLine);
        }
    }

    // ================== Níveis de log ==================
    public void debug(String text) { log(LogLevel.DEBUG, text); }
    public void info(String text)  { log(LogLevel.INFO, text); }
    public void error(String text) { log(LogLevel.ERROR, text); }

    // ================== Eventos comuns ==================
    public void logConnectionEstablished(String address) {
        info("Conexão estabelecida com: " + address);
    }

    public void logConnectionClosed(String address) {
        info("Conexão encerrada com: " + address);
    }

    public void logMessageSent(String message) {
        debug("Mensagem enviada: " + escape(message));
    }

    public void logMessageReceived(String message) {
        debug("Mensagem recebida: " + escape(message));
    }

    public void logMessageStatus(String status, String details) {
        info("Status da mensagem: " + status + ". Detalhes: " + details);
    }

    public void logCommunicationError(String errorMsg) {
        error("Erro de comunicação: " + errorMsg);
    }

    public void logConfigChange(String config, String oldValue, String newValue) {
        info("Alteração de configuração: " + config +
             " de '" + oldValue + "' para '" + newValue + "'.");
    }

    // ================== Buffer em memória ==================
    private void addToBuffer(String line) {
        if (logBuffer.size() >= bufferSize) {
            logBuffer.removeFirst();
        }
        logBuffer.addLast(line);
    }

    /**
     * Retorna os últimos logs armazenados em memória.
     */
    public synchronized List<String> getRecentLogs() {
        return new ArrayList<>(logBuffer);
    }

    /**
     * Limpa o buffer em memória.
     */
    public synchronized void clearBuffer() {
        logBuffer.clear();
        debug("Buffer de logs em memória limpo");
    }

    // ================== Auxiliares ==================
    private String escape(String s) {
        return s.replace("\r", "\\r").replace("\n", "\\n");
    }

    private String escapeJson(String s) {
        return escape(s).replace("\"", "\\\"");
    }

    private void checkRotate() {
        try {
            File file = new File(filename);
            if (file.length() >= MAX_FILE_SIZE) {
                writer.close();
                
                // Cria novo arquivo com timestamp
                String timestamp = LocalDateTime.now()
                        .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                String newFilename = filename.replace(".txt", "_" + timestamp + ".txt");
                
                // Renomeia o arquivo atual
                file.renameTo(new File(newFilename));
                
                // Cria novo arquivo
                filename = filename.replace(".txt", "_" + timestamp + ".txt");
                writer = new PrintWriter(new FileWriter(filename, true), true);
                
                info("Arquivo de log rotacionado. Novo arquivo: " + filename);
            }
        } catch (IOException e) {
            error("Erro ao rotacionar arquivo de log: " + e.getMessage());
        }
    }

    /**
     * Força a rotação do arquivo de log.
     */
    public synchronized void forceRotate() {
        try {
            if (writer != null) {
                writer.close();
                
                String timestamp = LocalDateTime.now()
                        .format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
                String newFilename = filename.replace(".txt", "_forced_" + timestamp + ".txt");
                
                File oldFile = new File(filename);
                if (oldFile.exists()) {
                    oldFile.renameTo(new File(newFilename));
                }
                
                filename = new File(getLogsDirectoryPath(), "logs_" + timestamp + ".txt").getAbsolutePath();
                writer = new PrintWriter(new FileWriter(filename, true), true);
                
                info("Rotação forçada do arquivo de log. Novo arquivo: " + filename);
            }
        } catch (IOException e) {
            error("Erro na rotação forçada: " + e.getMessage());
        }
    }

    // ================== AutoCloseable ==================
    @Override
    public void close() {
        if (writer != null) {
            info("LogService sendo encerrado");
            writer.close();
            writer = null;
        }
        
        // Limpa arquivos antigos ao fechar (mantém os últimos 10)
        cleanupOldLogs(10);
    }

    /**
     * Método finalize para garantir que os recursos sejam liberados.
     */
    @Override
    protected void finalize() throws Throwable {
        try {
            close();
        } finally {
            super.finalize();
        }
    }
}