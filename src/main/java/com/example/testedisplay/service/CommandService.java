package com.example.testedisplay.service;

/**
 * Classe utilitária para gerar comandos padrão para o display.
 */
public class CommandService {

    /**
     * Gera comando para definir mensagem inicial.
     */
    public static String initialMsg(String msg) {
        return "InitialMsg=" + msg + "\\r\\n";
    }

    /**
     * Gera comando para configurar baud rate, paridade, bits e stop bits.
     * @param baud ex: 19200
     * @param parity ex: n, o, e
     * @param dataBits ex: 8
     * @param stopBits ex: 1
     */
    public static String serialSettings(int baud, String parity, int dataBits, int stopBits) {
        return String.format("SerialSettings=%d,%s,%d,%d\\r\\n", baud, parity, dataBits, stopBits);
    }

    /**
     * Comando para salvar configurações.
     */
    public static String save() {
        return "Save\\r\\n";
    }

    /**
     * Comando para configurar protocolo (ex.: W01)
     */
    public static String protocol(String code) {
        return "Protocol=" + code + "\\r\\n";
    }

    /**
     * Comando para tipo de comunicação (232 ou 485)
     */
    public static String commType(String type) {
        return "CommType=" + type + "\\r\\n";
    }

    /**
     * Comando para configuração geral (pode ser estendido)
     */
    public static String config() {
        return "Config\\r\\n";
    }
}
