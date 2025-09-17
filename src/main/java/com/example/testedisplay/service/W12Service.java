package com.example.testedisplay.service;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Serviço para construir pacotes do Protocolo W12 para o WT-DISPLAY.
 */
public class W12Service {

    /**
     * Constrói o pacote de 22 bytes do protocolo W12.
     *
     * @param language 0:PT, 1:EN, 2:ES
     * @param message 0:Peso, 1:Pare, 2:Aguarde, 3:Siga
     * @param leftIsBlinking true se o semáforo esquerdo pisca
     * @param leftColor 0:Apagado, 1:Verde, 2:Vermelho, 3:Amarelo
     * @param rightIsBlinking true se o semáforo direito pisca
     * @param rightColor 0:Apagado, 1:Verde, 2:Vermelho, 3:Amarelo
     * @param weight String de 6 dígitos (ex: "123.45", "-12345")
     * @param sign Sinal do peso ('+' ou '-')
     * @param unit Unidade (ex: "kg")
     * @param bargraphDirection 0:Esq->Dir, 1:Dir->Esq
     * @param bargraphValue 0-96
     * @return pacote de 22 bytes.
     */
    public static byte[] buildPacket(
            int language, int message,
            boolean leftIsBlinking, int leftColor,
            boolean rightIsBlinking, int rightColor,
            String weight, char sign, String unit,
            int bargraphDirection, int bargraphValue) {

        byte[] packet = new byte[22];
        Arrays.fill(packet, (byte) ' '); // Preenche com espaços

        // Byte 0: Constante '0'
        packet[0] = '0';

        // Byte 1: Idioma
        packet[1] = (byte) Character.forDigit(language, 10);

        // Byte 2: Mensagem
        packet[2] = (byte) Character.forDigit(message, 10);

        // Byte 3: Semáforo Esquerdo
        byte leftSemaphoreByte = (byte) (leftColor & 0b11); // Garante que a cor use só 2 bits
        if (leftIsBlinking) {
            leftSemaphoreByte |= (1 << 7); // Seta bit 7 para piscar
        }
        packet[3] = leftSemaphoreByte;

        // Byte 4: Semáforo Direito
        byte rightSemaphoreByte = (byte) (rightColor & 0b11); // Garante que a cor use só 2 bits
        if (rightIsBlinking) {
            rightSemaphoreByte |= (1 << 7); // Seta bit 7 para piscar
        }
        packet[4] = rightSemaphoreByte;

        // Byte 5: Separador
        packet[5] = ' ';

        // Byte 6: Sinal
        packet[6] = (byte) sign;

        // Bytes 7-12: Peso (6 dígitos)
        String formattedWeight = String.format("%6s", weight);
        if (formattedWeight.length() > 6) formattedWeight = formattedWeight.substring(0, 6);
        byte[] weightBytes = formattedWeight.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(weightBytes, 0, packet, 7, weightBytes.length);

        // Bytes 13-14: Unidade
        String formattedUnit = String.format("%-2s", unit);
        if (formattedUnit.length() > 2) formattedUnit = formattedUnit.substring(0, 2);
        byte[] unitBytes = formattedUnit.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(unitBytes, 0, packet, 13, unitBytes.length);

        // Byte 15: Separador
        packet[15] = ' ';

        // Byte 16: Direção do Bargraph
        packet[16] = (byte) Character.forDigit(bargraphDirection, 10);

        // Bytes 17-18: Valor do Bargraph
        String bargraphStr = String.format("%02d", bargraphValue);
        byte[] bargraphBytes = bargraphStr.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bargraphBytes, 0, packet, 17, 2);

        // Byte 19: Separador
        packet[19] = ' ';

        // Byte 20: CR
        packet[20] = 0x0D;

        // Byte 21: LF
        packet[21] = 0x0A;

        return packet;
    }
}
