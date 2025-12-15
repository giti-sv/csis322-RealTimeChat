import java.io.IOException;
import java.net.*;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;

public class UDPServer {

    private static final int PORT = 12346;
    private static final int BUF_SIZE = 8192;

    private final DatagramSocket socket;

    private final Map<SocketAddress, String> clients = Collections.synchronizedMap(new HashMap<>());

    public UDPServer() throws SocketException {
        socket = new DatagramSocket(PORT);
    }

    public static void main(String[] args) {
        try {
            new UDPServer().start();
        } catch (Exception e) {
            System.err.println("UDP Server error: " + e.getMessage());
        }
    }

    public void start() {
        System.out.println("UDP Server listening on port " + PORT);
        byte[] buf = new byte[BUF_SIZE];

        while (true) {
            try {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);

                String msg = new String(packet.getData(), packet.getOffset(), packet.getLength(), "UTF-8").trim();
                handlePacket(msg, packet.getSocketAddress());

            } catch (IOException e) {
                System.err.println("UDP receive error: " + e.getMessage());
            }
        }
    }

    private void handlePacket(String packetStr, SocketAddress from) {
        String[] parts = packetStr.split("\\|", 4);
        if (parts.length != 4) {
            sendSystemTo(from, "Invalid packet format.");
            return;
        }

        String user = parts[0];
        String time = parts[1];
        String field3 = parts[2];
        String recvHash = parts[3];

        String plainText = Crypto.tryDecrypt(field3);

        String base = user + "|" + time + "|" + plainText;
        String computed = sha256(base);

        if (!computed.equals(recvHash)) {
            sendSystemTo(from, "CORRUPTED MESSAGE DETECTED. Ignoring.");
            return;
        }

        boolean isNew = !clients.containsKey(from);
        clients.putIfAbsent(from, user);

        if (isNew) {
            System.out.println(user + " connected from " + from);
            broadcast(buildPacket("SERVER", now(), user + " joined the chat."), from);
        }

        if (plainText.startsWith("/")) {
            handleCommand(user, plainText, from);
            return;
        }

        String outPacket = buildPacket(user, time, plainText);

        broadcast(outPacket, from);
        sendRaw(from, outPacket);

        System.out.println("[" + time + "] " + user + ": " + plainText);
    }

    private void handleCommand(String user, String cmdLine, SocketAddress from) {
        if (cmdLine.equalsIgnoreCase("/quit")) {
            clients.remove(from);
            sendSystemTo(from, "Goodbye!");
            broadcast(buildPacket("SERVER", now(), user + " left the chat."), from);
            System.out.println(user + " quit from " + from);
            return;
        }

        if (cmdLine.equalsIgnoreCase("/list")) {
            sendSystemTo(from, "Online users: " + String.join(", ", uniqueUsers()));
            return;
        }

        if (cmdLine.toLowerCase().startsWith("/whisper")) {
            String[] p = cmdLine.split("\\s+", 3);
            if (p.length < 3) {
                sendSystemTo(from, "Usage: /whisper <user> <message>");
                return;
            }
            doWhisper(user, p[1], p[2], from);
            return;
        }

        sendSystemTo(from, "Unknown command. Available: /quit, /list, /whisper <user> <msg>");
    }

    private void doWhisper(String fromUser, String targetUser, String message, SocketAddress fromAddr) {
        SocketAddress targetAddr = null;

        synchronized (clients) {
            for (Map.Entry<SocketAddress, String> e : clients.entrySet()) {
                if (e.getValue().equalsIgnoreCase(targetUser)) {
                    targetAddr = e.getKey();
                    break;
                }
            }
        }

        if (targetAddr == null) {
            sendSystemTo(fromAddr, "User '" + targetUser + "' not found.");
            return;
        }

        String time = now();
        String taggedPlain = "[WHISPER] " + message;
        String packet = buildPacket(fromUser, time, taggedPlain);

        sendRaw(targetAddr, packet);
        sendRaw(fromAddr, packet);
    }

    private List<String> uniqueUsers() {
        Set<String> set = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        synchronized (clients) {
            set.addAll(clients.values());
        }
        return new ArrayList<>(set);
    }

    private void broadcast(String packet, SocketAddress exclude) {
        synchronized (clients) {
            for (SocketAddress addr : clients.keySet()) {
                if (!addr.equals(exclude)) {
                    sendRaw(addr, packet);
                }
            }
        }
    }

    private void sendSystemTo(SocketAddress addr, String plainMsg) {
        sendRaw(addr, buildPacket("SERVER", now(), plainMsg));
    }

    private void sendRaw(SocketAddress addr, String msg) {
        try {
            byte[] data = msg.getBytes("UTF-8");
            DatagramPacket p = new DatagramPacket(data, data.length);
            p.setSocketAddress(addr);
            socket.send(p);
        } catch (IOException ignored) {
        }
    }

    private static String now() {
        return new SimpleDateFormat("HH:mm:ss").format(new Date());
    }

    private static String sha256(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest)
                sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return "ERROR_HASH";
        }
    }

    private static String buildPacket(String user, String time, String plainText) {
        String cipherHex = Crypto.encrypt(plainText);
        String base = user + "|" + time + "|" + plainText;
        String hash = sha256(base);
        return user + "|" + time + "|" + cipherHex + "|" + hash;
    }
}
