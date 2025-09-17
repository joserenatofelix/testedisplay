package com.example.testedisplay.model;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Gerenciador de configurações modernizado com suporte a backup,
 * tipos de dados diversos e tratamento robusto de exceções.
 */
public class ConfigManager {
    private static final Logger LOGGER = Logger.getLogger(ConfigManager.class.getName());
    
    private final Properties props = new Properties();
    private final File configFile;
    private final File backupDir;
    
    // Valores padrão para configurações
    private static final String DEFAULT_SERIAL_PORT = "COM3";
    private static final String DEFAULT_BAUD_RATE = "19200";
    private static final String DEFAULT_IP = "127.0.0.1";
    private static final String DEFAULT_PORT = "5000";
    private static final String DEFAULT_THEME = "light";
    private static final boolean DEFAULT_AUTO_CONNECT = false;
    private static final int DEFAULT_RECONNECT_ATTEMPTS = 3;

    public ConfigManager() {
        this("config.properties");
    }

    public ConfigManager(String configFileName) {
        this.configFile = new File(configFileName);
        this.backupDir = new File("config_backups");
        ensureBackupDirectoryExists();
        load();
    }

    /**
     * Carrega as configurações do arquivo, criando-o com valores padrão se não existir.
     */
    public void load() {
        if (configFile.exists()) {
            try (InputStream in = new FileInputStream(configFile)) {
                props.load(in);
                LOGGER.info("Configurações carregadas de: " + configFile.getAbsolutePath());
                createBackup(); // Cria backup após carregamento bem-sucedido
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Erro ao carregar configurações: " + e.getMessage(), e);
                loadDefaults(); // Carrega valores padrão em caso de erro
            }
        } else {
            loadDefaults();
            save(); // Salva valores padrão no novo arquivo
        }
    }

    /**
     * Carrega valores padrão para todas as configurações.
     */
    private void loadDefaults() {
        props.setProperty("serialPort", DEFAULT_SERIAL_PORT);
        props.setProperty("baudRate", DEFAULT_BAUD_RATE);
        props.setProperty("ip", DEFAULT_IP);
        props.setProperty("port", DEFAULT_PORT);
        props.setProperty("theme", DEFAULT_THEME);
        props.setProperty("autoConnect", Boolean.toString(DEFAULT_AUTO_CONNECT));
        props.setProperty("reconnectAttempts", Integer.toString(DEFAULT_RECONNECT_ATTEMPTS));
        props.setProperty("windowWidth", "1100");
        props.setProperty("windowHeight", "700");
        props.setProperty("windowX", "100");
        props.setProperty("windowY", "100");
        props.setProperty("logLevel", "INFO");
        
        LOGGER.info("Configurações padrão carregadas");
    }

    /**
     * Salva as configurações atuais no arquivo.
     */
    public void save() {
        try (OutputStream out = new FileOutputStream(configFile)) {
            props.store(out, "Configurações do testedisplay - " + java.time.LocalDateTime.now());
            LOGGER.info("Configurações salvas em: " + configFile.getAbsolutePath());
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Erro ao salvar configurações: " + e.getMessage(), e);
        }
    }

    /**
     * Cria um backup do arquivo de configuração atual.
     */
    private void createBackup() {
        if (!configFile.exists()) return;
        
        try {
            String timestamp = java.time.LocalDateTime.now()
                    .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String backupFileName = "config_" + timestamp + ".properties";
            Path backupPath = Paths.get(backupDir.getAbsolutePath(), backupFileName);
            
            Files.copy(configFile.toPath(), backupPath, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("Backup criado: " + backupPath);
            
            // Limita o número de backups mantidos (últimos 10)
            limitBackups(10);
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Não foi possível criar backup: " + e.getMessage(), e);
        }
    }

    /**
     * Limita o número de backups mantidos, removendo os mais antigos.
     */
    private void limitBackups(int maxBackups) {
        File[] backups = backupDir.listFiles((dir, name) -> name.startsWith("config_") && name.endsWith(".properties"));
        if (backups != null && backups.length > maxBackups) {
            // Ordena por data de modificação (mais antigo primeiro)
            java.util.Arrays.sort(backups, java.util.Comparator.comparingLong(File::lastModified));
            
            for (int i = 0; i < backups.length - maxBackups; i++) {
                if (backups[i].delete()) {
                    LOGGER.info("Backup antigo removido: " + backups[i].getName());
                }
            }
        }
    }

    /**
     * Garante que o diretório de backups existe.
     */
    private void ensureBackupDirectoryExists() {
        if (!backupDir.exists() && !backupDir.mkdirs()) {
            LOGGER.warning("Não foi possível criar diretório de backups: " + backupDir.getAbsolutePath());
        }
    }

    /**
     * Obtém um valor string da configuração.
     */
    public String get(String key, String defaultValue) {
        return props.getProperty(key, defaultValue);
    }

    /**
     * Obtém um valor inteiro da configuração.
     */
    public int getInt(String key, int defaultValue) {
        try {
            return Integer.parseInt(props.getProperty(key));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Obtém um valor booleano da configuração.
     */
    public boolean getBoolean(String key, boolean defaultValue) {
        String value = props.getProperty(key);
        if (value == null) return defaultValue;
        return Boolean.parseBoolean(value);
    }

    /**
     * Obtém um valor double da configuração.
     */
    public double getDouble(String key, double defaultValue) {
        try {
            return Double.parseDouble(props.getProperty(key));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    /**
     * Define um valor string na configuração.
     */
    public void set(String key, String value) {
        props.setProperty(key, value);
    }

    /**
     * Define um valor inteiro na configuração.
     */
    public void set(String key, int value) {
        props.setProperty(key, Integer.toString(value));
    }

    /**
     * Define um valor booleano na configuração.
     */
    public void set(String key, boolean value) {
        props.setProperty(key, Boolean.toString(value));
    }

    /**
     * Define um valor double na configuração.
     */
    public void set(String key, double value) {
        props.setProperty(key, Double.toString(value));
    }

    /**
     * Remove uma configuração.
     */
    public void remove(String key) {
        props.remove(key);
    }

    /**
     * Verifica se uma configuração existe.
     */
    public boolean contains(String key) {
        return props.containsKey(key);
    }

    /**
     * Retorna todas as configurações como um conjunto de propriedades.
     */
    public Properties getAllProperties() {
        return new Properties(props);
    }

    /**
     * Restaura a configuração padrão para todas as propriedades.
     */
    public void restoreDefaults() {
        props.clear();
        loadDefaults();
        save();
        LOGGER.info("Configurações restauradas para os valores padrão");
    }

    /**
     * Restaura a partir do backup mais recente.
     */
    public boolean restoreFromBackup() {
        File[] backups = backupDir.listFiles((dir, name) -> name.startsWith("config_") && name.endsWith(".properties"));
        if (backups == null || backups.length == 0) {
            LOGGER.warning("Nenhum backup disponível para restauração");
            return false;
        }

        // Encontra o backup mais recente
        File latestBackup = java.util.Arrays.stream(backups)
                .max(java.util.Comparator.comparingLong(File::lastModified))
                .orElse(null);

        if (latestBackup != null) {
            try {
                Files.copy(latestBackup.toPath(), configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                load(); // Recarrega as configurações do backup
                LOGGER.info("Configurações restauradas do backup: " + latestBackup.getName());
                return true;
            } catch (IOException e) {
                LOGGER.log(Level.SEVERE, "Erro ao restaurar do backup: " + e.getMessage(), e);
            }
        }

        return false;
    }

    /**
     * Retorna o caminho do arquivo de configuração.
     */
    public String getConfigFilePath() {
        return configFile.getAbsolutePath();
    }

    /**
     * Retorna o caminho do diretório de backups.
     */
    public String getBackupDirectoryPath() {
        return backupDir.getAbsolutePath();
    }
}