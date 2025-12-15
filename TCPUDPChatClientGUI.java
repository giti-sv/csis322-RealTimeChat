import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * CSIS322 Real-Time Chat Application - Combined TCP/UDP GUI Client (Swing)
 *
 * Incoming packets:
 * user|time|cipherHexOrPlain|sha256(user|time|plainText)
 *
 * - We verify integrity using plaintext (decrypting field 3 if needed).
 * - We display plaintext in the chat area.
 *
 * Outgoing:
 * - TCP:
 * - first line: username (plaintext)
 * - commands (start with '/'): plaintext
 * - normal messages: Crypto.encrypt(plainText) (sent as a single line)
 * - UDP:
 * - always send 4-field packet
 * - field 3 plaintext for commands, ciphertext hex for normal messages
 *
 * BONUS FEATURE:
 * - Message History (client-side): loads on connect, appends on send/receive,
 * stored locally in ./history/
 * - Command: /clearhistory
 */
public class TCPUDPChatClientGUI extends JFrame {

    private enum Mode {
        TCP, UDP
    }

    private static final int TCP_DEFAULT_PORT = 12345;
    private static final int UDP_DEFAULT_PORT = 12346;
    private static final int UDP_BUF_SIZE = 8192;

    private JComboBox<String> cmbMode;
    private JTextField txtHost;
    private JTextField txtPort;
    private JTextField txtUsername;
    private JTextArea txtChat;
    private JTextField txtMessage;
    private JButton btnConnect;
    private JButton btnDisconnect;
    private JButton btnSend;

    private volatile boolean connected = false;
    private Mode currentMode = Mode.TCP;
    private String username = "User";

    // For history key (so each connection saves/loads the correct file)
    private String sessionHost = "127.0.0.1";
    private int sessionPort = TCP_DEFAULT_PORT;

    private Socket tcpSocket;
    private BufferedReader tcpIn;
    private PrintWriter tcpOut;
    private Thread tcpListenerThread;

    private DatagramSocket udpSocket;
    private InetAddress udpServerAddress;
    private int udpServerPort;
    private Thread udpListenerThread;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new TCPUDPChatClientGUI().setVisible(true));
    }

    public TCPUDPChatClientGUI() {
        super("TCP/UDP Chat Client (CSIS322)");
        buildUI();
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 600);
        setLocationRelativeTo(null);
    }

    private void buildUI() {
        JPanel top = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(6, 6, 6, 6);
        c.fill = GridBagConstraints.HORIZONTAL;

        cmbMode = new JComboBox<>(new String[] { "TCP", "UDP" });
        cmbMode.addActionListener(e -> {
            currentMode = cmbMode.getSelectedIndex() == 0 ? Mode.TCP : Mode.UDP;
            if (!connected) {
                txtPort.setText(String.valueOf(currentMode == Mode.TCP ? TCP_DEFAULT_PORT : UDP_DEFAULT_PORT));
            }
        });

        txtHost = new JTextField("127.0.0.1");
        txtPort = new JTextField(String.valueOf(TCP_DEFAULT_PORT));
        txtUsername = new JTextField("User");

        btnConnect = new JButton("Connect");
        btnDisconnect = new JButton("Disconnect");
        btnSend = new JButton("Send");

        btnDisconnect.setEnabled(false);
        btnSend.setEnabled(false);

        btnConnect.addActionListener(this::onConnect);
        btnDisconnect.addActionListener(e -> disconnect());
        btnSend.addActionListener(e -> sendMessage());

        int col = 0;
        addField(top, c, col++, "Host", txtHost);
        addField(top, c, col++, "Port", txtPort);
        addField(top, c, col++, "Username", txtUsername);

        c.gridx = col++;
        c.gridy = 0;
        top.add(new JLabel("Mode"), c);
        c.gridx = col++;
        top.add(cmbMode, c);

        c.gridx = col++;
        top.add(btnConnect, c);
        c.gridx = col++;
        top.add(btnDisconnect, c);

        txtChat = new JTextArea();
        txtChat.setEditable(false);
        txtChat.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 13));
        JScrollPane scroll = new JScrollPane(txtChat);

        JPanel bottom = new JPanel(new BorderLayout(8, 8));
        txtMessage = new JTextField();
        txtMessage.addActionListener(e -> sendMessage());
        bottom.add(txtMessage, BorderLayout.CENTER);
        bottom.add(btnSend, BorderLayout.EAST);
        bottom.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));

        setLayout(new BorderLayout());
        add(top, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(bottom, BorderLayout.SOUTH);

        appendChat("Commands: /list  /whisper <user> <msg>  /quit  /clearhistory\n");
    }

    private void addField(JPanel panel, GridBagConstraints c, int x, String label, JComponent field) {
        c.gridx = x;
        c.gridy = 0;
        panel.add(new JLabel(label), c);
        c.gridy = 1;
        panel.add(field, c);
    }

    private void onConnect(ActionEvent e) {
        if (connected)
            return;

        String host = txtHost.getText().trim();
        String portStr = txtPort.getText().trim();
        username = txtUsername.getText().trim();
        if (username.isEmpty())
            username = "User";

        int port;
        try {
            port = Integer.parseInt(portStr);
        } catch (NumberFormatException ex) {
            showMessage("Port must be a number.");
            return;
        }

        // Save for history file key
        sessionHost = host;
        sessionPort = port;

        if (currentMode == Mode.TCP)
            connectTCP(host, port);
        else
            connectUDP(host, port);
    }

    private void connectTCP(String host, int port) {
        try {
            tcpSocket = new Socket(host, port);
            tcpIn = new BufferedReader(new InputStreamReader(tcpSocket.getInputStream(), StandardCharsets.UTF_8));
            tcpOut = new PrintWriter(new OutputStreamWriter(tcpSocket.getOutputStream(), StandardCharsets.UTF_8), true);

            tcpOut.println(username);

            connected = true;
            setConnectedUI(true);

            appendChat("Connected via TCP to " + host + ":" + port + " as " + username + "\n");
            loadHistoryToChat();

            tcpListenerThread = new Thread(this::listenTCP, "TCP-Listener");
            tcpListenerThread.setDaemon(true);
            tcpListenerThread.start();

        } catch (IOException ex) {
            appendChat("TCP connect error: " + ex.getMessage() + "\n");
            disconnect();
        }
    }

    private void connectUDP(String host, int port) {
        try {
            udpServerAddress = InetAddress.getByName(host);
            udpServerPort = port;
            udpSocket = new DatagramSocket();

            connected = true;
            setConnectedUI(true);

            appendChat("Connected via UDP to " + host + ":" + port + " as " + username + "\n");
            loadHistoryToChat();

            udpListenerThread = new Thread(this::listenUDP, "UDP-Listener");
            udpListenerThread.setDaemon(true);
            udpListenerThread.start();

        } catch (Exception ex) {
            appendChat("UDP connect error: " + ex.getMessage() + "\n");
            disconnect();
        }
    }

    private void loadHistoryToChat() {
        String mode = currentMode.name();
        var lines = History.load(username, mode, sessionHost, sessionPort);

        if (!lines.isEmpty()) {
            appendChat("\n----- Message History Loaded (" + lines.size() + " lines) -----\n");
            for (String line : lines)
                appendChat(line + "\n");
            appendChat("----- End of History -----\n\n");
        } else {
            appendChat("(No previous history found for this user/session)\n");
        }
    }

    private void setConnectedUI(boolean isConnected) {
        SwingUtilities.invokeLater(() -> {
            btnConnect.setEnabled(!isConnected);
            btnDisconnect.setEnabled(isConnected);
            btnSend.setEnabled(isConnected);
            cmbMode.setEnabled(!isConnected);
        });
    }

    private void sendMessage() {
        if (!connected) {
            showMessage("Not connected.");
            return;
        }

        String msg = txtMessage.getText().trim();
        if (msg.isEmpty())
            return;

        txtMessage.setText("");

        // Bonus command
        if (msg.equalsIgnoreCase("/clearhistory")) {
            History.clear(username, currentMode.name(), sessionHost, sessionPort);
            appendChat("[SYSTEM] History cleared.\n");
            return;
        }

        if (currentMode == Mode.TCP)
            sendTCP(msg);
        else
            sendUDP(msg);

        // Local echo for message history (only for non-commands)
        if (!msg.startsWith("/")) {
            String line = "[" + now() + "] " + username + ": " + msg;
            appendChat(line + "\n");
            History.append(username, currentMode.name(), sessionHost, sessionPort, line);
        }

        if (msg.equalsIgnoreCase("/quit")) {
            disconnect();
        }
    }

    private void sendTCP(String text) {
        if (tcpOut == null) {
            showMessage("TCP connection not available.");
            return;
        }

        if (text.startsWith("/"))
            tcpOut.println(text);
        else
            tcpOut.println(Crypto.encrypt(text));
    }

    private void sendUDP(String text) {
        if (udpSocket == null || udpServerAddress == null) {
            showMessage("UDP connection not available.");
            return;
        }

        try {
            String time = now();

            String plainText = text;
            String field3 = text.startsWith("/") ? text : Crypto.encrypt(text);

            String base = username + "|" + time + "|" + plainText;
            String hash = sha256(base);

            String packetStr = username + "|" + time + "|" + field3 + "|" + hash;

            byte[] data = packetStr.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(data, data.length, udpServerAddress, udpServerPort);
            udpSocket.send(packet);

        } catch (IOException ex) {
            appendChat("UDP send error: " + ex.getMessage() + "\n");
        }
    }

    private void listenTCP() {
        try {
            String line;
            while (connected && tcpIn != null && (line = tcpIn.readLine()) != null) {
                handleIncoming(line);
            }
        } catch (IOException ex) {
            if (connected)
                appendChat("TCP connection lost: " + ex.getMessage() + "\n");
        } finally {
            disconnect();
        }
    }

    private void listenUDP() {
        byte[] buffer = new byte[UDP_BUF_SIZE];
        while (connected && udpSocket != null && !udpSocket.isClosed()) {
            try {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                udpSocket.receive(packet);

                String line = new String(packet.getData(), packet.getOffset(), packet.getLength(),
                        StandardCharsets.UTF_8).trim();
                handleIncoming(line);

            } catch (IOException ex) {
                if (connected)
                    appendChat("UDP receive error: " + ex.getMessage() + "\n");
                break;
            }
        }
        disconnect();
    }

    private void handleIncoming(String line) {
        String[] parts = line.split("\\|", 4);
        if (parts.length == 4) {
            String user = parts[0];
            String time = parts[1];
            String field3 = parts[2];
            String recvHash = parts[3];

            String plainText = Crypto.tryDecrypt(field3);

            String base = user + "|" + time + "|" + plainText;
            String computed = sha256(base);

            if (!computed.equals(recvHash)) {
                String corrupted = "[CORRUPTED MESSAGE DETECTED]";
                appendChat(corrupted + "\n");
                History.append(username, currentMode.name(), sessionHost, sessionPort, corrupted);
                return;
            }

            String finalLine = "[" + time + "] " + user + ": " + plainText;
            appendChat(finalLine + "\n");
            History.append(username, currentMode.name(), sessionHost, sessionPort, finalLine);

        } else {
            appendChat(line + "\n");
            History.append(username, currentMode.name(), sessionHost, sessionPort, line);
        }
    }

    private void disconnect() {
        connected = false;

        try {
            if (tcpSocket != null)
                tcpSocket.close();
        } catch (IOException ignored) {
        }
        try {
            if (udpSocket != null)
                udpSocket.close();
        } catch (Exception ignored) {
        }

        tcpSocket = null;
        tcpIn = null;
        tcpOut = null;
        udpSocket = null;
        udpServerAddress = null;

        setConnectedUI(false);
    }

    private void appendChat(String s) {
        SwingUtilities.invokeLater(() -> {
            txtChat.append(s);
            txtChat.setCaretPosition(txtChat.getDocument().getLength());
        });
    }

    private void showMessage(String s) {
        JOptionPane.showMessageDialog(this, s);
    }

    private static String now() {
        return new SimpleDateFormat("HH:mm:ss").format(new Date());
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest)
                sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "ERROR_HASH";
        }
    }
}
