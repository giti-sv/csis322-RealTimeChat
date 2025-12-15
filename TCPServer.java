import java.io.*;
import java.net.*;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * CSIS322 Real-Time Chat Application - TCP Server
 *
 * Packet format (sent to clients):
 * username|timestamp|cipherTextHex|sha256(username|timestamp|plainText)
 *
 * Notes:
 * - Clients send: (1) username as the first line (plaintext)
 * - Then:
 * - Commands start with "/" and are sent plaintext (e.g., /list, /whisper,
 * /quit)
 * - Normal messages are sent encrypted (hex) using Crypto.encrypt(...)
 * - Server always broadcasts packets (even system messages), so clients can
 * verify integrity.
 */
public class TCPServer {

    private static final int PORT = 12345;

    private final Set<ClientHandler> clients = Collections.synchronizedSet(new HashSet<>());

    public static void main(String[] args) {
        new TCPServer().start();
    }

    public void start() {
        System.out.println("TCP Server listening on port " + PORT);
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket client = serverSocket.accept();
                ClientHandler handler = new ClientHandler(client);
                clients.add(handler);
                new Thread(handler).start();
            }
        } catch (IOException e) {
            System.err.println("TCP Server error: " + e.getMessage());
        }
    }

    private void broadcast(String packet, ClientHandler exclude) {
        synchronized (clients) {
            for (ClientHandler c : clients) {
                if (c != exclude) {
                    c.sendPacketRaw(packet);
                }
            }
        }
    }

    private void removeClient(ClientHandler handler) {
        clients.remove(handler);
        sendSystemBroadcast(handler.username + " left the chat.", handler);
        System.out.println(handler.username + " disconnected.");
    }

    private void sendSystemTo(ClientHandler target, String plainMsg) {
        String packet = buildPacket("SERVER", now(), plainMsg);
        target.sendPacketRaw(packet);
    }

    private void sendSystemBroadcast(String plainMsg, ClientHandler exclude) {
        String packet = buildPacket("SERVER", now(), plainMsg);
        broadcast(packet, exclude);
    }

    private void sendUserList(ClientHandler requester) {
        StringBuilder sb = new StringBuilder();
        sb.append("Online users: ");
        boolean first = true;
        synchronized (clients) {
            for (ClientHandler c : clients) {
                if (c.username != null) {
                    if (!first)
                        sb.append(", ");
                    sb.append(c.username);
                    first = false;
                }
            }
        }
        sendSystemTo(requester, sb.toString());
    }

    private ClientHandler findUser(String username) {
        synchronized (clients) {
            for (ClientHandler c : clients) {
                if (c.username != null && c.username.equalsIgnoreCase(username))
                    return c;
            }
        }
        return null;
    }

    private void whisper(String targetUser, String plainMessage, ClientHandler sender) {
        ClientHandler target = findUser(targetUser);
        if (target == null) {
            sendSystemTo(sender, "User '" + targetUser + "' not found.");
            return;
        }

        String time = now();
        String taggedPlain = "[WHISPER] " + plainMessage;

        String packetToTarget = buildPacket(sender.username, time, taggedPlain);
        target.sendPacketRaw(packetToTarget);

        String packetToSender = buildPacket(sender.username, time, taggedPlain);
        sender.sendPacketRaw(packetToSender);
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

    private class ClientHandler implements Runnable {
        private final Socket socket;
        private BufferedReader in;
        private PrintWriter out;
        private String username;

        ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
                out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

                username = in.readLine();
                if (username == null || username.trim().isEmpty()) {
                    username = "User" + socket.getPort();
                }
                username = username.trim();

                sendSystemTo(this, "Welcome " + username + "! Type /quit to leave.");
                sendSystemBroadcast(username + " joined the chat.", this);
                System.out.println(username + " connected from " + socket.getRemoteSocketAddress());

                String line;
                while ((line = in.readLine()) != null) {

                    if (line.startsWith("/")) {
                        handleCommand(line.trim());
                        continue;
                    }

                    String time = now();
                    String plainText = Crypto.tryDecrypt(line);

                    String packet = buildPacket(username, time, plainText);

                    broadcast(packet, this);
                    sendPacketRaw(packet);

                    System.out.println("[" + time + "] " + username + ": " + plainText);
                }

            } catch (IOException e) {
                System.err.println("Connection error with " + username + ": " + e.getMessage());
            } finally {
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
                removeClient(this);
            }
        }

        private void handleCommand(String cmdLine) {
            if (cmdLine.equalsIgnoreCase("/quit")) {
                sendSystemTo(this, "Goodbye!");
                try {
                    socket.close();
                } catch (IOException ignored) {
                }
                return;
            }

            if (cmdLine.equalsIgnoreCase("/list")) {
                sendUserList(this);
                return;
            }

            if (cmdLine.toLowerCase().startsWith("/whisper")) {
                String[] parts = cmdLine.split("\\s+", 3);
                if (parts.length < 3) {
                    sendSystemTo(this, "Usage: /whisper <user> <message>");
                    return;
                }
                whisper(parts[1], parts[2], this);
                return;
            }

            sendSystemTo(this, "Unknown command. Available: /quit, /list, /whisper <user> <msg>");
        }

        void sendPacketRaw(String packet) {
            out.println(packet);
        }
    }
}
