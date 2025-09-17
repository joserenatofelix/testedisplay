package com.example.testedisplay;

import javax.swing.SwingUtilities;
import com.example.testedisplay.gui.MainFrame;

/**
 * Classe principal que inicia a aplicação.
 * Inicializa a interface gráfica (MainFrame) de forma thread-safe
 * usando SwingUtilities.
 */
public class Main {
    public static void main(String[] args) {
        // Cria a interface gráfica na thread de eventos do Swing
        SwingUtilities.invokeLater(() -> {
            try {
                new MainFrame();
            } catch (Exception e) {
                e.printStackTrace();
                System.err.println("Erro ao iniciar a aplicação: " + e.getMessage());
            }
        });
    }
}