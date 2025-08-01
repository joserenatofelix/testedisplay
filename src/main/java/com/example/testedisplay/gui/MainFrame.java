package com.example.testedisplay.gui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;

import com.example.testedisplay.model.ConfigManager;
import com.example.testedisplay.service.LogService;
import com.example.testedisplay.service.SerialService;
import com.example.testedisplay.service.TcpService;
import com.fazecast.jSerialComm.SerialPort;
import com.itextpdf.text.Document;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfWriter;

/**
 * Interface principal da aplicação de testes do WT-DISPLAY.
 */
public class MainFrame extends JFrame {
    private static final long serialVersionUID = 1L;

    // Componentes da interface
    private JComboBox<String> comboBoxSerial = new JComboBox<>();
    private JComboBox<String> comboBoxBaudRate = new JComboBox<>(new String[] {"9600", "19200", "38400", "57600", "115200"});
    private JTextField txtIp = new JTextField("127.0.0.1", 10);
    private JTextField txtPort = new JTextField("5000", 5);
    private JTextField txtCommand = new JTextField("Config\\r\\n", 20);
    private JTextArea sentCommandsArea = new JTextArea();
    private JTextArea receivedLogsArea = new JTextArea();
    private JLabel statusLabel = new JLabel("Desconectado");

    // Serviços
    private SerialService serialService = new SerialService();
    private TcpService tcpService = new TcpService();
    private LogService logService = new LogService();
    private ConfigManager config = new ConfigManager();

    public MainFrame() {
        setTitle("testedisplay");
        setSize(900, 500);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setLayout(new BorderLayout());

        initMenu();
        initUI();
        loadConfig();
        detectSerialPorts();

        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                serialService.disconnect();
                tcpService.disconnect();
                logService.info("Aplicação encerrada.");
                logService.close();
                saveConfig();
                System.exit(0);
            }
        });

        logService.info("Aplicação iniciada.");
        setVisible(true);
    }

    private void initMenu() {
        JMenuBar menuBar = new JMenuBar();
        JMenu ajudaMenu = new JMenu("Ajuda");
        JMenuItem sobreItem = new JMenuItem("Sobre");
        sobreItem.addActionListener(e -> JOptionPane.showMessageDialog(this,
                "testedisplay\nVersão 1.0\nAutor: Renato Félix"));
        ajudaMenu.add(sobreItem);
        menuBar.add(ajudaMenu);
        setJMenuBar(menuBar);
    }

    private void initUI() {
        JPanel configPanel = new JPanel();
        configPanel.add(new JLabel("Serial:"));
        configPanel.add(comboBoxSerial);
        configPanel.add(new JLabel("Baud:"));
        configPanel.add(comboBoxBaudRate);
        JButton btnSerial = new JButton("Conectar Serial");
        btnSerial.addActionListener(this::connectSerial);
        configPanel.add(btnSerial);

        configPanel.add(new JLabel("IP:"));
        configPanel.add(txtIp);
        configPanel.add(new JLabel("Porta:"));
        configPanel.add(txtPort);
        JButton btnIP = new JButton("Conectar IP");
        btnIP.addActionListener(this::connectIP);
        configPanel.add(btnIP);

        statusLabel.setForeground(Color.RED);
        configPanel.add(statusLabel);
        add(configPanel, BorderLayout.NORTH);

        JPanel center = new JPanel(new GridLayout(1, 2));
        sentCommandsArea.setEditable(false);
        receivedLogsArea.setEditable(false);
        center.add(new JScrollPane(sentCommandsArea));
        center.add(new JScrollPane(receivedLogsArea));
        add(center, BorderLayout.CENTER);

        JPanel bottom = new JPanel();
        bottom.add(new JLabel("Comando:"));
        bottom.add(txtCommand);
        JButton btnSend = new JButton("Enviar");
        btnSend.addActionListener(this::sendCommand);
        bottom.add(btnSend);

        String[] quicks = {
            "Config\\r\\n", "Save\\r\\n", "CommType=232\\r\\n", "CommType=485\\r\\n",
            "SerialSettings=9600,n,8,1\\r\\n", "InitialMsg=HELLO\\r\\n", "Protocol=W01\\r\\n"
        };
        for (String q : quicks) {
            JButton b = new JButton(q.contains("=") ? q.split("=")[0] : q.trim());
            b.addActionListener(e -> sendQuick(q));
            bottom.add(b);
        }

        // Correção: criar botão, depois adicionar ActionListener
        JButton btnLimpar = new JButton("Limpar Tela");
        btnLimpar.addActionListener(e -> clearLogs());
        bottom.add(btnLimpar);

        JButton btnAbrirLogs = new JButton("Abrir Pasta de Logs");
        btnAbrirLogs.addActionListener(e -> openLogsFolder());
        bottom.add(btnAbrirLogs);

        JButton btnExportarPdf = new JButton("Exportar Log para PDF");
        btnExportarPdf.addActionListener(e -> exportLogToPdf());
        bottom.add(btnExportarPdf);

        add(bottom, BorderLayout.SOUTH);
    }

    private void detectSerialPorts() {
        comboBoxSerial.removeAllItems();
        for (SerialPort port : SerialPort.getCommPorts()) {
            comboBoxSerial.addItem(port.getSystemPortName());
        }
        logService.debug("Portas seriais detectadas.");
    }

    private void connectSerial(ActionEvent e) {
        try {
            String port = (String) comboBoxSerial.getSelectedItem();
            int baud = Integer.parseInt((String) comboBoxBaudRate.getSelectedItem());
            if (serialService.connect(port, baud, logService)) {
                logService.info("Conectado serial: " + port + "," + baud);
                addLog("Conectado serial: " + port + "," + baud);
                updateStatus(true);
            } else {
                logService.error("Falha ao conectar serial: " + port);
                addLog("Falha ao conectar serial.");
                updateStatus(false);
            }
        } catch (Exception ex) {
            logService.error("Erro config serial: " + ex.getMessage());
            addLog("Erro config serial: " + ex.getMessage());
            updateStatus(false);
        }
    }

    private void connectIP(ActionEvent e) {
        try {
            String ip = txtIp.getText();
            int port = Integer.parseInt(txtPort.getText());
            if (tcpService.connect(ip, port, logService)) {
                logService.info("Conectado IP: " + ip + ":" + port);
                addLog("Conectado IP: " + ip + ":" + port);
                updateStatus(true);
            } else {
                logService.error("Falha ao conectar IP: " + ip + ":" + port);
                addLog("Falha ao conectar IP.");
                updateStatus(false);
            }
        } catch (Exception ex) {
            logService.error("Erro config IP: " + ex.getMessage());
            addLog("Erro config IP: " + ex.getMessage());
            updateStatus(false);
        }
    }

    private void sendCommand(ActionEvent e) {
        send(txtCommand.getText());
    }

    private void sendQuick(String cmd) {
        send(cmd);
    }

    private void send(String cmd) {
        cmd = cmd.replace("\\r", "\r").replace("\\n", "\n");
        try {
            if (serialService.isConnected()) {
                serialService.send(cmd);
                sentCommandsArea.append("Serial >> " + cmd.replace("\r", "\\r").replace("\n", "\\n") + "\n");
                logService.debug("Comando enviado pela Serial: " + cmd);
            } else if (tcpService.isConnected()) {
                tcpService.send(cmd);
                sentCommandsArea.append("IP >> " + cmd.replace("\r", "\\r").replace("\n", "\\n") + "\n");
                logService.debug("Comando enviado via IP: " + cmd);
            } else {
                logService.error("Tentativa de enviar sem conexão ativa.");
                addLog("Nenhuma conexão ativa.");
            }
        } catch (Exception ex) {
            logService.error("Erro ao enviar: " + ex.getMessage());
            addLog("Erro ao enviar: " + ex.getMessage());
        }
    }

    private void addLog(String msg) {
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        SwingUtilities.invokeLater(() -> {
            receivedLogsArea.append(timestamp + " - " + msg + "\n");
            receivedLogsArea.setCaretPosition(receivedLogsArea.getDocument().getLength());
        });
    }

    private void clearLogs() {
        sentCommandsArea.setText("");
        receivedLogsArea.setText("");
        logService.debug("Tela de logs limpa.");
    }

    private void openLogsFolder() {
        try {
            Desktop.getDesktop().open(new File("."));
            logService.debug("Pasta de logs aberta.");
        } catch (Exception ex) {
            logService.error("Erro ao abrir pasta: " + ex.getMessage());
            addLog("Erro ao abrir pasta: " + ex.getMessage());
        }
    }

    private void exportLogToPdf() {
        try {
            String text = receivedLogsArea.getText();
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            String filename = "LogExport_" + timestamp + ".pdf";

            Document doc = new Document();
            PdfWriter.getInstance(doc, new java.io.FileOutputStream(filename));
            doc.open();
            doc.add(new Paragraph(text));
            doc.close();

            logService.info("Log exportado para PDF: " + filename);
            addLog("Exportado para PDF: " + filename);

            int res = JOptionPane.showConfirmDialog(this, "Deseja abrir o PDF?");
            if (res == JOptionPane.YES_OPTION) {
                Desktop.getDesktop().open(new File(filename));
            }
        } catch (Exception ex) {
            logService.error("Erro ao exportar PDF: " + ex.getMessage());
            addLog("Erro ao exportar PDF: " + ex.getMessage());
        }
    }

    private void updateStatus(boolean connected) {
        statusLabel.setText(connected ? "Conectado" : "Desconectado");
        statusLabel.setForeground(connected ? Color.GREEN : Color.RED);
    }

    private void loadConfig() {
        comboBoxSerial.setSelectedItem(config.get("serialPort", "COM3"));
        comboBoxBaudRate.setSelectedItem(config.get("baudRate", "19200"));
        txtIp.setText(config.get("ip", "127.0.0.1"));
        txtPort.setText(config.get("port", "5000"));
        logService.debug("Configurações carregadas.");
    }

    private void saveConfig() {
        config.set("serialPort", (String) comboBoxSerial.getSelectedItem());
        config.set("baudRate", (String) comboBoxBaudRate.getSelectedItem());
        config.set("ip", txtIp.getText());
        config.set("port", txtPort.getText());
        config.save();
        logService.debug("Configurações salvas.");
    }
}
