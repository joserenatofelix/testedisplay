package com.example.testedisplay.service;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Serviço avançado para geração e validação de comandos para o display WT-DISPLAY.
 * Oferece enums tipados, validação de parâmetros e suporte a comandos avançados.
 */
public class CommandService {
    
    // Padrões de validação
    private static final Pattern MSG_PATTERN = Pattern.compile("^[\\x20-\\x7E]{0,20}$"); // ASCII imprimível, até 20 chars
    private static final Pattern PROTOCOL_PATTERN = Pattern.compile("^W[0-9]{2}$"); // W seguido de 2 dígitos
    private static final Pattern PARITY_PATTERN = Pattern.compile("^[noe]$"); // n, o, ou e
    
    // Enums para parâmetros tipados
    public enum Parity {
        NONE("n"), ODD("o"), EVEN("e");
        
        private final String value;
        
        Parity(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
        
        public static Parity fromString(String value) {
            for (Parity parity : values()) {
                if (parity.value.equalsIgnoreCase(value)) {
                    return parity;
                }
            }
            throw new IllegalArgumentException("Paridade inválida: " + value);
        }
    }
    
    public enum CommType {
        RS232("232"), RS485("485");
        
        private final String value;
        
        CommType(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
        
        public static CommType fromString(String value) {
            for (CommType type : values()) {
                if (type.value.equals(value)) {
                    return type;
                }
            }
            throw new IllegalArgumentException("Tipo de comunicação inválido: " + value);
        }
    }
    
    public enum BaudRate {
        BAUDRATE_9600(9600),
        BAUDRATE_19200(19200),
        BAUDRATE_38400(38400),
        BAUDRATE_57600(57600),
        BAUDRATE_115200(115200);
        
        private final int value;
        
        BaudRate(int value) {
            this.value = value;
        }
        
        public int getValue() {
            return value;
        }
    }
    
    public enum DisplayColor {
        RED("Red"),
        GREEN("Green"),
        YELLOW("Yellow"),
        BLUE("Blue"),
        WHITE("White"),
        CYAN("Cyan"),
        MAGENTA("Magenta");
        
        private final String value;
        
        DisplayColor(String value) {
            this.value = value;
        }
        
        public String getValue() {
            return value;
        }
    }
    
    // Mapa de comandos pré-definidos para acesso rápido
    private static final Map<String, String> QUICK_COMMANDS = new HashMap<>();
    
    static {
        // Comandos básicos
        QUICK_COMMANDS.put("CONFIG", "Config\\r\\n");
        QUICK_COMMANDS.put("SAVE", "Save\\r\\n");
        QUICK_COMMANDS.put("RESTART", "Restart\\r\\n");
        QUICK_COMMANDS.put("RESET", "Reset\\r\\n");
        QUICK_COMMANDS.put("VERSION", "Version\\r\\n");
        QUICK_COMMANDS.put("STATUS", "Status\\r\\n");
        
        // Comandos de comunicação
        QUICK_COMMANDS.put("RS232", "CommType=232\\r\\n");
        QUICK_COMMANDS.put("RS485", "CommType=485\\r\\n");
        
        // Configurações de serial pré-definidas
        QUICK_COMMANDS.put("SERIAL_9600", "SerialSettings=9600,n,8,1\\r\\n");
        QUICK_COMMANDS.put("SERIAL_19200", "SerialSettings=19200,n,8,1\\r\\n");
        QUICK_COMMANDS.put("SERIAL_38400", "SerialSettings=38400,n,8,1\\r\\n");
        
        // Protocolos comuns
        QUICK_COMMANDS.put("PROTOCOL_W01", "Protocol=W01\\r\\n");
        QUICK_COMMANDS.put("PROTOCOL_W02", "Protocol=W02\\r\\n");
    }

    /**
     * Gera comando para definir mensagem inicial com validação.
     */
    public static String initialMsg(String msg) {
        validateMessage(msg);
        return "InitialMsg=" + msg + "\\r\\n";
    }
    
    /**
     * Gera comando para mensagem inicial com cor específica.
     */
    public static String initialMsg(String msg, DisplayColor color) {
        validateMessage(msg);
        return String.format("InitialMsg=%s|Color=%s\\r\\n", msg, color.getValue());
    }
    
    /**
     * Gera comando para configurar baud rate, paridade, bits e stop bits com validação.
     */
    public static String serialSettings(int baud, Parity parity, int dataBits, int stopBits) {
        validateSerialParams(baud, dataBits, stopBits);
        return String.format("SerialSettings=%d,%s,%d,%d\\r\\n", 
                            baud, parity.getValue(), dataBits, stopBits);
    }
    
    /**
     * Gera comando para configurar baud rate, paridade, bits e stop bits (sobrecarga com string).
     */
    public static String serialSettings(int baud, String parity, int dataBits, int stopBits) {
        return serialSettings(baud, Parity.fromString(parity), dataBits, stopBits);
    }
    
    /**
     * Gera comando para configurar baud rate usando enum.
     */
    public static String serialSettings(BaudRate baudRate, Parity parity, int dataBits, int stopBits) {
        return serialSettings(baudRate.getValue(), parity, dataBits, stopBits);
    }

    /**
     * Comando para salvar configurações.
     */
    public static String save() {
        return "Save\\r\\n";
    }

    /**
     * Comando para configurar protocolo com validação.
     */
    public static String protocol(String code) {
        validateProtocol(code);
        return "Protocol=" + code + "\\r\\n";
    }

    /**
     * Comando para tipo de comunicação usando enum.
     */
    public static String commType(CommType type) {
        return "CommType=" + type.getValue() + "\\r\\n";
    }
    
    /**
     * Comando para tipo de comunicação (sobrecarga com string).
     */
    public static String commType(String type) {
        return commType(CommType.fromString(type));
    }

    /**
     * Comando para configuração geral.
     */
    public static String config() {
        return "Config\\r\\n";
    }
    
    /**
     * Comando para reiniciar o display.
     */
    public static String restart() {
        return "Restart\\r\\n";
    }
    
    /**
     * Comando para resetar para configurações de fábrica.
     */
    public static String reset() {
        return "Reset\\r\\n";
    }
    
    /**
     * Comando para obter versão do firmware.
     */
    public static String version() {
        return "Version\\r\\n";
    }
    
    /**
     * Comando para obter status do display.
     */
    public static String status() {
        return "Status\\r\\n";
    }
    
    /**
     * Comando para definir brilho do display (0-100).
     */
    public static String brightness(int level) {
        if (level < 0 || level > 100) {
            throw new IllegalArgumentException("Brilho deve estar entre 0 e 100");
        }
        return "Brightness=" + level + "\\r\\n";
    }
    
    /**
     * Comando para definir timeout do display (em segundos).
     */
    public static String timeout(int seconds) {
        if (seconds < 0 || seconds > 3600) {
            throw new IllegalArgumentException("Timeout deve estar entre 0 e 3600 segundos");
        }
        return "Timeout=" + seconds + "\\r\\n";
    }
    
    /**
     * Comando para exibir mensagem temporária no display.
     */
    public static String showMessage(String message, int durationSeconds) {
        validateMessage(message);
        if (durationSeconds < 1 || durationSeconds > 3600) {
            throw new IllegalArgumentException("Duração deve estar entre 1 e 3600 segundos");
        }
        return String.format("ShowMessage=%s|Duration=%d\\r\\n", message, durationSeconds);
    }
    
    /**
     * Comando para limpar a tela do display.
     */
    public static String clearScreen() {
        return "Clear\\r\\n";
    }
    
    /**
     * Obtém um comando pré-definido pelo nome.
     */
    public static String getQuickCommand(String commandName) {
        String command = QUICK_COMMANDS.get(commandName.toUpperCase());
        if (command == null) {
            throw new IllegalArgumentException("Comando rápido não encontrado: " + commandName);
        }
        return command;
    }
    
    /**
     * Retorna todos os comandos rápidos disponíveis.
     */
    public static Map<String, String> getAllQuickCommands() {
        return new HashMap<>(QUICK_COMMANDS);
    }
    
    /**
     * Adiciona um novo comando rápido ao mapa.
     */
    public static void addQuickCommand(String name, String command) {
        QUICK_COMMANDS.put(name.toUpperCase(), command);
    }
    
    /**
     * Remove um comando rápido do mapa.
     */
    public static void removeQuickCommand(String name) {
        QUICK_COMMANDS.remove(name.toUpperCase());
    }
    
    /**
     * Converte um comando com sequências de escape para bytes reais.
     */
    public static byte[] convertToBytes(String command) {
        String processed = command.replace("\\r", "\r").replace("\\n", "\n");
        return processed.getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
    
    /**
     * Converte bytes recebidos para string com sequências de escape.
     */
    public static String convertFromBytes(byte[] data) {
        String result = new String(data, java.nio.charset.StandardCharsets.UTF_8);
        return result.replace("\r", "\\r").replace("\n", "\\n");
    }
    
    /**
     * Valida parâmetros de configuração serial.
     */
    private static void validateSerialParams(int baud, int dataBits, int stopBits) {
        if (baud != 9600 && baud != 19200 && baud != 38400 && baud != 57600 && baud != 115200) {
            throw new IllegalArgumentException("Baud rate inválido. Use: 9600, 19200, 38400, 57600 ou 115200");
        }
        
        if (dataBits != 7 && dataBits != 8) {
            throw new IllegalArgumentException("Data bits deve ser 7 ou 8");
        }
        
        if (stopBits != 1 && stopBits != 2) {
            throw new IllegalArgumentException("Stop bits deve ser 1 ou 2");
        }
    }
    
    /**
     * Valida mensagem para o display.
     */
    private static void validateMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            throw new IllegalArgumentException("Mensagem não pode ser vazia");
        }
        
        if (!MSG_PATTERN.matcher(message).matches()) {
            throw new IllegalArgumentException("Mensagem contém caracteres inválidos ou é muito longa (máx. 10 caracteres)");
        }
    }
    
    /**
     * Valida código de protocolo.
     */
    private static void validateProtocol(String protocol) {
        if (!PROTOCOL_PATTERN.matcher(protocol).matches()) {
            throw new IllegalArgumentException("Protocolo deve estar no formato W## (ex: W01, W02)");
        }
    }
    
    /**
     * Valida string de paridade.
     */
    private static void validateParity(String parity) {
        if (!PARITY_PATTERN.matcher(parity).matches()) {
            throw new IllegalArgumentException("Paridade deve ser 'n' (none), 'o' (odd) ou 'e' (even)");
        }
    }
    
    /**
     * Verifica se um comando é válido para o display.
     */
    public static boolean isValidCommand(String command) {
        if (command == null || command.trim().isEmpty()) {
            return false;
        }
        
        // Comandos devem terminar com \r\n (escapado como \\r\\n)
        return command.endsWith("\\r\\n");
    }
    
    /**
     * Extrai o nome do comando (parte antes do = ou comando simples).
     */
    public static String extractCommandName(String fullCommand) {
        if (fullCommand == null) return "";
        
        String cleanCommand = fullCommand.replace("\\r\\n", "");
        if (cleanCommand.contains("=")) {
            return cleanCommand.split("=")[0];
        }
        return cleanCommand;
    }
    
    /**
     * Extrai o valor do parâmetro de um comando.
     */
    public static String extractCommandValue(String fullCommand) {
        if (fullCommand == null || !fullCommand.contains("=")) {
            return "";
        }
        
        String cleanCommand = fullCommand.replace("\\r\\n", "");
        return cleanCommand.split("=", 2)[1];
    }
}