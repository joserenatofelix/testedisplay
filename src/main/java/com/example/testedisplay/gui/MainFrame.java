package com.example.testedisplay.gui;

import com.example.testedisplay.model.ConfigManager;
import com.example.testedisplay.service.LogService;
import com.example.testedisplay.service.SerialService;
import com.example.testedisplay.service.TcpService;
import com.example.testedisplay.service.W12Service;
import com.fazecast.jSerialComm.SerialPort;
import com.itextpdf.text.Document;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.pdf.PdfWriter;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;

public class MainFrame extends JFrame {
    private static final long serialVersionUID = 1L;

    // --- Componentes da Interface ---
    private JComboBox<String> comboBoxSerial = new JComboBox<>();
    private JComboBox<String> comboBoxBaudRate = new JComboBox<>(new String[]{"9600", "19200", "38400", "57600", "115200"});
    private JTextField txtIp = new JTextField("127.0.0.1", 10);
    private JTextField txtPort = new JTextField("5000", 5);
    private JLabel statusLabel = new JLabel("Desconectado");
    private JButton portStatusButton;
    private JTextArea portMonitorLogArea;

    private JTextField txtCommand = new JTextField("Config\r\n", 20);
    private JTextArea sentCommandsArea = new JTextArea();
    private JTextArea receivedLogsArea = new JTextArea();

    private JComboBox<String> w12Language = new JComboBox<>(new String[]{"Português", "Inglês", "Espanhol"});
    private JComboBox<String> w12Message = new JComboBox<>(new String[]{"Valor de peso", "Pare", "Aguarde", "Siga"});
    private JCheckBox w12LeftBlinking = new JCheckBox("Piscando");
    private JComboBox<String> w12LeftColor = new JComboBox<>(new String[]{"Apagado", "Verde", "Vermelho", "Amarelo"});
    private JCheckBox w12RightBlinking = new JCheckBox("Piscando");
    private JComboBox<String> w12RightColor = new JComboBox<>(new String[]{"Apagado", "Verde", "Vermelho", "Amarelo"});
    private JComboBox<Character> w12WeightSign = new JComboBox<>(new Character[]{'+', '-'});
    private JTextField w12WeightValue = new JTextField("123.45", 6);
    private JTextField w12Unit = new JTextField("kg", 2);
    private JComboBox<String> w12BargraphDir = new JComboBox<>(new String[]{"Esq -> Dir", "Dir -> Esq"});
    private JSpinner w12BargraphValue = new JSpinner(new SpinnerNumberModel(50, 0, 96, 1));

    // --- Serviços ---
    private SerialService serialService = new SerialService();
    private TcpService tcpService = new TcpService();
    private LogService logService = new LogService();
    private ConfigManager config = new ConfigManager();

    private final StringBuilder logBuffer = new StringBuilder();
    private final Timer logTimer = new Timer(250, e -> flushLogBuffer());
    private JToggleButton togglePortMonitorButton;
    private Timer portMonitorTimer;

    private byte[] lastW12PacketSent = null;
    private String lastTextCommandSent = null;

    public MainFrame() {
        setTitle("testedisplay V2.0");
        setSize(1200, 700);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setLayout(new BorderLayout());

        initMenu();
        initUI();
        loadConfig();
        detectSerialPorts();
        startPortMonitor();

        addWindowListener(new WindowAdapter() {
            public void windowClosing(WindowEvent e) {
                serialService.disconnect();
                tcpService.disconnect();
                logService.info("Aplicação encerrada.");
                logService.close();
                saveConfig();
                logTimer.stop();
                if (portMonitorTimer != null) {
                    portMonitorTimer.stop();
                }
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
                "testedisplay\nVersão 2.0\nAutor: Renato Félix & Gemini", "Sobre", JOptionPane.INFORMATION_MESSAGE));
        ajudaMenu.add(sobreItem);
        menuBar.add(ajudaMenu);
        setJMenuBar(menuBar);
    }

    private void initUI() {
        add(createConnectionPanel(), BorderLayout.NORTH);

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.addTab("Terminal de Comandos", createTerminalTab());
        tabbedPane.addTab("Protocolo W12", createW12Tab());

        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, tabbedPane, createPortStatusPanel());
        splitPane.setResizeWeight(0.8);
        add(splitPane, BorderLayout.CENTER);

        add(createActionsPanel(), BorderLayout.SOUTH);
    }

    private JPanel createPortStatusPanel() {
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setBorder(BorderFactory.createTitledBorder("Monitoramento da Porta"));

        portStatusButton = new JButton("INDETERMINADO");
        portStatusButton.setFont(new Font("Segoe UI", Font.BOLD, 18));
        portStatusButton.setFocusPainted(false);
        portStatusButton.setOpaque(true);
        portStatusButton.setBackground(UIManager.getColor("Panel.background"));
        portStatusButton.setForeground(UIManager.getColor("Label.foreground"));
        statusPanel.add(portStatusButton, BorderLayout.NORTH);

        portMonitorLogArea = new JTextArea();
        portMonitorLogArea.setEditable(false);
        portMonitorLogArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        JScrollPane scrollPane = new JScrollPane(portMonitorLogArea);
        statusPanel.add(scrollPane, BorderLayout.CENTER);

        return statusPanel;
    }

    private JPanel createConnectionPanel() {
        JPanel configPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        configPanel.add(new JLabel("Serial:"));
        configPanel.add(comboBoxSerial);
        configPanel.add(new JLabel("Baud:"));
        configPanel.add(comboBoxBaudRate);
        JButton btnSerial = new JButton("Conectar Serial");
        btnSerial.addActionListener(this::connectSerial);
        configPanel.add(btnSerial);

        configPanel.add(new JSeparator(SwingConstants.VERTICAL));

        configPanel.add(new JLabel("IP:"));
        configPanel.add(txtIp);
        configPanel.add(new JLabel("Porta:"));
        configPanel.add(txtPort);
        JButton btnIP = new JButton("Conectar IP");
        btnIP.addActionListener(this::connectIP);
        configPanel.add(btnIP);

        configPanel.add(new JSeparator(SwingConstants.VERTICAL));

        JButton btnDesconectar = new JButton("Desconectar");
        btnDesconectar.addActionListener(e -> desconectar());
        configPanel.add(btnDesconectar);

        configPanel.add(new JSeparator(SwingConstants.VERTICAL));

        statusLabel.setForeground(Color.RED);
        configPanel.add(statusLabel);

        togglePortMonitorButton = new JToggleButton("Monitorar Porta");
        togglePortMonitorButton.addActionListener(e -> togglePortMonitor());
        configPanel.add(togglePortMonitorButton);

        return configPanel;
    }

    private JPanel createTerminalTab() {
        JPanel terminalPanel = new JPanel(new BorderLayout());

        JPanel logsPanel = new JPanel(new GridLayout(1, 2));
        sentCommandsArea.setEditable(false);
        receivedLogsArea.setEditable(false);
        logsPanel.add(new JScrollPane(sentCommandsArea));
        logsPanel.add(new JScrollPane(receivedLogsArea));
        terminalPanel.add(logsPanel, BorderLayout.CENTER);

        JPanel commandPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        commandPanel.add(new JLabel("Comando:"));
        commandPanel.add(txtCommand);
        JButton btnSend = new JButton("Enviar");
        btnSend.addActionListener(this::sendCommand);
        commandPanel.add(btnSend);

        String[] quicks = {
            "Config\r\n", "Save\r\n", "CommType=232\r\n", "CommType=485\r\n",
            "SerialSettings=9600,n,8,1\r\n", "InitialMsg=HELLO\r\n", "Protocol=W01\r\n"
        };
        for (String q : quicks) {
            JButton b = new JButton(q.contains("=") ? q.split("=")[0] : q.trim());
            b.addActionListener(e -> sendQuick(q));
            commandPanel.add(b);
        }
        terminalPanel.add(commandPanel, BorderLayout.SOUTH);

        return terminalPanel;
    }

    private JPanel createW12Tab() {
        JPanel w12Panel = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(5, 5, 5, 5);
        gbc.anchor = GridBagConstraints.WEST;

        gbc.gridx = 0; gbc.gridy = 0; w12Panel.add(new JLabel("Idioma:"), gbc);
        gbc.gridx = 1; w12Panel.add(w12Language, gbc);
        gbc.gridx = 2; w12Panel.add(new JLabel("Mensagem Pré-definida:"), gbc);
        gbc.gridx = 3; w12Panel.add(w12Message, gbc);

        gbc.gridx = 0; gbc.gridy = 1; w12Panel.add(new JLabel("Semáforo Esquerdo:"), gbc);
        gbc.gridx = 1; w12Panel.add(w12LeftColor, gbc);
        gbc.gridx = 2; w12Panel.add(w12LeftBlinking, gbc);

        gbc.gridx = 0; gbc.gridy = 2; w12Panel.add(new JLabel("Semáforo Direito:"), gbc);
        gbc.gridx = 1; w12Panel.add(w12RightColor, gbc);
        gbc.gridx = 2; w12Panel.add(w12RightBlinking, gbc);

        gbc.gridx = 0; gbc.gridy = 3; w12Panel.add(new JLabel("Peso (Sinal + Valor):"), gbc);
        JPanel weightPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        weightPanel.add(w12WeightSign);
        weightPanel.add(w12WeightValue);
        gbc.gridx = 1; w12Panel.add(weightPanel, gbc);
        gbc.gridx = 2; w12Panel.add(new JLabel("Unidade:"), gbc);
        gbc.gridx = 3; w12Panel.add(w12Unit, gbc);

        gbc.gridx = 0; gbc.gridy = 4; w12Panel.add(new JLabel("Bargraph (Direção + Valor):"), gbc);
        JPanel bargraphPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        bargraphPanel.add(w12BargraphDir);
        bargraphPanel.add(w12BargraphValue);
        gbc.gridx = 1; w12Panel.add(bargraphPanel, gbc);

        gbc.gridx = 0; gbc.gridy = 5; gbc.gridwidth = 4; gbc.anchor = GridBagConstraints.CENTER;
        JButton sendW12Button = new JButton("Enviar Pacote W12");
        sendW12Button.addActionListener(this::sendW12Packet);
        w12Panel.add(sendW12Button, gbc);

        gbc.gridy = 6; gbc.weighty = 1.0;
        w12Panel.add(new JLabel(), gbc);

        return w12Panel;
    }

    private JPanel createActionsPanel() {
        JPanel bottom = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton btnLimpar = new JButton("Limpar Tela");
        btnLimpar.addActionListener(e -> clearLogs());
        bottom.add(btnLimpar);

        JButton btnAbrirLogs = new JButton("Abrir Pasta de Logs");
        btnAbrirLogs.addActionListener(e -> openLogsFolder());
        bottom.add(btnAbrirLogs);

        JButton btnExportarPdf = new JButton("Exportar Log para PDF");
        btnExportarPdf.addActionListener(e -> exportLogToPdf());
        bottom.add(btnExportarPdf);

        JButton btnRestart = new JButton("Restart Painel");
        btnRestart.addActionListener(e -> restartPainel());
        bottom.add(btnRestart);

        JButton btnClearMonitorLog = new JButton("Limpar Log Monitor");
        btnClearMonitorLog.addActionListener(e -> clearPortMonitorLog());
        bottom.add(btnClearMonitorLog);

        return bottom;
    }

    private void detectSerialPorts() {
        comboBoxSerial.removeAllItems();
        for (SerialPort port : SerialPort.getCommPorts()) {
            comboBoxSerial.addItem(port.getSystemPortName());
        }
        logService.debug("Portas seriais detectadas.");
    }

    private void startPortMonitor() {
        portMonitorTimer = new Timer(500, e -> updatePortStatus());
        portMonitorTimer.setInitialDelay(0);
    }

    private void togglePortMonitor() {
        if (togglePortMonitorButton.isSelected()) {
            portMonitorTimer.start();
            logService.debug("Monitoramento de porta iniciado.");
        } else {
            portMonitorTimer.stop();
            portStatusButton.setText("MONITOR DESATIVADO");
            portStatusButton.setBackground(UIManager.getColor("Panel.background"));
            portStatusButton.setForeground(UIManager.getColor("Label.foreground"));
            logService.debug("Monitoramento de porta parado.");
        }
    }

    private void updatePortStatus() {
        String selectedPort = (String) comboBoxSerial.getSelectedItem();
        if (selectedPort == null) {
            portStatusButton.setText("N/A");
            portStatusButton.setBackground(UIManager.getColor("Panel.background"));
            portStatusButton.setForeground(UIManager.getColor("Label.foreground"));
            return;
        }

        if (serialService.isConnected() && selectedPort.equals(serialService.getCurrentPortName())) {
            portStatusButton.setText("CONECTADO");
            portStatusButton.setBackground(Color.RED);
            portStatusButton.setForeground(Color.WHITE);
            return;
        }

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
        new PortStatusWorker(selectedPort, timestamp).execute();
    }

    private class PortStatusWorker extends SwingWorker<SerialService.PortStatus, Void> {
        private final String portName;
        private final String timestamp;

        public PortStatusWorker(String portName, String timestamp) {
            this.portName = portName;
            this.timestamp = timestamp;
        }

        @Override
        protected SerialService.PortStatus doInBackground() throws Exception {
            return serialService.checkPortStatus(portName, logService);
        }

        @Override
        protected void done() {
            try {
                SerialService.PortStatus status = get();
                String statusText;
                Color bgColor;
                Color fgColor;
                String logEntry;

                switch (status) {
                    case FREE:
                        statusText = "LIVRE";
                        bgColor = new Color(0, 128, 0);
                        fgColor = Color.WHITE;
                        logEntry = String.format("%s - %s: LIVRE ✓", timestamp, portName);
                        break;
                    case BUSY:
                        statusText = "OCUPADA";
                        bgColor = Color.RED;
                        fgColor = Color.WHITE;
                        logEntry = String.format("%s - %s: OCUPADA ✗", timestamp, portName);
                        break;
                    case NOT_FOUND:
                        statusText = "NÃO ENCONTRADA";
                        bgColor = Color.ORANGE;
                        fgColor = Color.BLACK;
                        logEntry = String.format("%s - %s: INDISPONÍVEL / NÃO ENCONTRADA", timestamp, portName);
                        break;
                    case ERROR:
                        statusText = "ERRO";
                        bgColor = Color.DARK_GRAY;
                        fgColor = Color.WHITE;
                        logEntry = String.format("%s - %s: ERRO", timestamp, portName);
                        break;
                    default:
                        statusText = "INDETERMINADO";
                        bgColor = UIManager.getColor("Panel.background");
                        fgColor = UIManager.getColor("Label.foreground");
                        logEntry = String.format("%s - %s: INDETERMINADO", timestamp, portName);
                        break;
                }
                portStatusButton.setText(statusText);
                portStatusButton.setBackground(bgColor);
                portStatusButton.setForeground(fgColor);
                logService.debug("Appending to portMonitorLogArea: " + logEntry);
                portMonitorLogArea.append(logEntry + "\n");
                portMonitorLogArea.setCaretPosition(portMonitorLogArea.getDocument().getLength());

            } catch (Exception e) {
                logService.error("Erro ao verificar status da porta: " + e.getMessage());
                portStatusButton.setText("ERRO");
                portStatusButton.setBackground(Color.DARK_GRAY);
                portStatusButton.setForeground(Color.WHITE);
                String logEntry = String.format("%s - %s: ERRO (%s)", timestamp, portName, e.getMessage());
                portMonitorLogArea.append(logEntry + "\n");
                portMonitorLogArea.setCaretPosition(portMonitorLogArea.getDocument().getLength());
            }
        }
    }

    private void connectSerial(ActionEvent e) {
        try {
            String port = (String) comboBoxSerial.getSelectedItem();
            int baud = Integer.parseInt((String) comboBoxBaudRate.getSelectedItem());
            if (serialService.connect(port, baud, logService)) {
                logService.info("Conectado serial: " + port + "," + baud);
                addLog("Conectado serial: " + port + "," + baud);
                updateStatus(true);
                resendLastDisplay(); // Attempt to resend last display on successful connection
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
        new SendWorker(cmd).execute();
    }

    private void send(byte[] data) {
        new SendWorker(data).execute();
    }

    private class SendWorker extends SwingWorker<Boolean, Void> {
        private final String stringData;
        private final byte[] byteData;
        private final boolean updateLastSent;

        public SendWorker(String data) {
            this(data, true);
        }

        public SendWorker(byte[] data) {
            this(data, true);
        }

        public SendWorker(String data, boolean updateLastSent) {
            this.stringData = data;
            this.byteData = null;
            this.updateLastSent = updateLastSent;
        }

        public SendWorker(byte[] data, boolean updateLastSent) {
            this.stringData = null;
            this.byteData = data;
            this.updateLastSent = updateLastSent;
        }

        @Override
        protected Boolean doInBackground() throws Exception {
            if (serialService.isConnected()) {
                if (stringData != null) {
                    return serialService.send(stringData);
                } else {
                    return serialService.send(byteData);
                }
            } else if (tcpService.isConnected()) {
                if (stringData != null) {
                    return tcpService.send(stringData);
                } else {
                    return tcpService.send(byteData);
                }
            }
            return false;
        }

        @Override
        protected void done() {
            try {
                boolean success = get();
                if (success) {
                    if (stringData != null) {
                        String logMsg = stringData.replace("\r", "\\r").replace("\n", "\\n");
                        sentCommandsArea.append("TXT >> " + logMsg + "\n");
                        logService.debug("Comando enviado: " + logMsg);
                        if (updateLastSent) {
                            lastTextCommandSent = stringData;
                            lastW12PacketSent = null; // Clear W12 if a text command is sent
                        }
                    } else {
                        StringBuilder hex = new StringBuilder("HEX >> ");
                        for (byte b : byteData) {
                            hex.append(String.format("%02X ", b));
                        }
                        sentCommandsArea.append(hex.toString().trim() + "\n");
                        logService.debug("Pacote de bytes enviado: " + hex.toString().trim());
                        if (updateLastSent) {
                            lastW12PacketSent = byteData;
                            lastTextCommandSent = null; // Clear text if a W12 packet is sent
                        }
                    }
                    // Always resend the last display after a successful command, unless it was a resend itself
                    if (updateLastSent) {
                        resendLastDisplay();
                    }
                } else {
                    logService.error("Tentativa de enviar sem conexão ativa.");
                    addLog("Nenhuma conexão ativa.");
                }
            } catch (Exception e) {
                logService.error("Erro ao enviar: " + e.getMessage());
                addLog("Erro ao enviar: " + e.getMessage());
            }
        }
    }

    private void sendW12Packet(ActionEvent e) {
        try {
            byte[] packet = W12Service.buildPacket(
                    w12Language.getSelectedIndex(),
                    w12Message.getSelectedIndex(),
                    w12LeftBlinking.isSelected(),
                    w12LeftColor.getSelectedIndex(),
                    w12RightBlinking.isSelected(),
                    w12RightColor.getSelectedIndex(),
                    w12WeightValue.getText(),
                    (char) w12WeightSign.getSelectedItem(),
                    w12Unit.getText(),
                    w12BargraphDir.getSelectedIndex(),
                    (int) w12BargraphValue.getValue()
            );
            send(packet);
        } catch (Exception ex) {
            logService.error("Erro ao construir pacote W12: " + ex.getMessage());
            JOptionPane.showMessageDialog(this, "Erro ao construir pacote W12: " + ex.getMessage(), "Erro W12", JOptionPane.ERROR_MESSAGE);
        }
    }

    private void addLog(String msg) {
        synchronized (logBuffer) {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            logBuffer.append(timestamp).append(" - ").append(msg).append("\n");
        }
        if (!logTimer.isRunning()) {
            logTimer.start();
        }
    }

    private void flushLogBuffer() {
        String content;
        synchronized (logBuffer) {
            content = logBuffer.toString();
            logBuffer.setLength(0);
        }
        SwingUtilities.invokeLater(() -> {
            receivedLogsArea.append(content);
            receivedLogsArea.setCaretPosition(receivedLogsArea.getDocument().getLength());
        });
    }

    private void clearLogs() {
        sentCommandsArea.setText("");
        receivedLogsArea.setText("");
        logService.debug("Tela de logs limpa.");
    }

    private void clearPortMonitorLog() {
        portMonitorLogArea.setText("");
        logService.debug("Log de monitoramento da porta limpo.");
    }

    private void openLogsFolder() {
        try {
            Desktop.getDesktop().open(new File("logs"));
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

            int res = JOptionPane.showConfirmDialog(this, "Deseja abrir o PDF?", "Exportação Concluída", JOptionPane.YES_NO_OPTION);
            if (res == JOptionPane.YES_OPTION) {
                Desktop.getDesktop().open(new File(filename));
            }
        } catch (Exception ex) {
            logService.error("Erro ao exportar PDF: " + ex.getMessage());
            addLog("Erro ao exportar PDF: " + ex.getMessage());
        }
    }

    private void restartPainel() {
        send("Restart\r\n");
        JOptionPane.showMessageDialog(this, "Comando de restart enviado.", "Restart", JOptionPane.INFORMATION_MESSAGE);
    }

    private void resendLastDisplay() {
        if (serialService.isConnected() || tcpService.isConnected()) {
            if (lastW12PacketSent != null) {
                // Use a new SendWorker to avoid recursion issues with the current SendWorker's done() method
                new SendWorker(lastW12PacketSent, false).execute(); // false indicates not to update last sent
            } else if (lastTextCommandSent != null) {
                // Only resend if it's a display-related command, not a config command
                // For now, we'll resend any text command, but this might need refinement
                // based on what commands actually update the display.
                new SendWorker(lastTextCommandSent, false).execute(); // false indicates not to update last sent
            }
        }
    }

    private void desconectar() {
        try {
            serialService.disconnect();
            tcpService.disconnect();
            updateStatus(false);
            logService.info("Desconectado e porta liberada.");
            addLog("Desconectado e porta liberada.");
        } catch (Exception ex) {
            logService.error("Erro ao desconectar: " + ex.getMessage());
            addLog("Erro ao desconectar: " + ex.getMessage());
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
