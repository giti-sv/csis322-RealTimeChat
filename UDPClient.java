import java.io.IOException;
import java.net.*;
import java.security.MessageDigest;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Scanner;

public class UDPClient {

    private static final String HOST = "127.0.0.1";
    private static final int PORT = 12346;

    private DatagramSocket socket;
    private InetAddress serverAddr;

    public static void main(String[] args) {
        new UDPClient().start();
    }

    public void start() {
        try (Scanner sc = new Scanner(System.in)) {
            serverAddr = InetAddress.getByName(HOST);
            socket = new DatagramSocket();

            System.out.print("Enter your username: ");
            String username = sc.nextLine().trim();
            if (username.isEmpty())
                username = "User";

            Thread receiver = new Thread(() -> receiveLoop());
            receiver.setDaemon(true);
            receiver.start();

            System.out.println("Connected via UDP to " + HOST + ":" + PORT + " as " + username);

            while (true) {
                String input = sc.nextLine();
                if (input == null)
                    break;
                input = input.trim();
                if (input.isEmpty())
                    continue;

                String time = now();

                String plainText = input;
                String field3 = input.startsWith("/") ? input : Crypto.encrypt(input);

                String base = username + "|" + time + "|" + plainText;
                String hash = sha256(base);

                String packet = username + "|" + time + "|" + field3 + "|" + hash;

                send(packet);

                if (input.equalsIgnoreCase("/quit"))
                    break;
            }

        } catch (Exception e) {
            System.err.println("UDP Client error: " + e.getMessage());
        } finally {
            if (socket != null && !socket.isClosed())
                socket.close();
        }
    }

    private void send(String packet) throws IOException {
        byte[] data = packet.getBytes("UTF-8");
        DatagramPacket p = new DatagramPacket(data, data.length, serverAddr, PORT);
        socket.send(p);
    }

    private void receiveLoop() {
        byte[] buf = new byte[8192];
        while (socket != null && !socket.isClosed()) {
            try {
                DatagramPacket p = new DatagramPacket(buf, buf.length);
                socket.receive(p);

                String msg = new String(p.getData(), p.getOffset(), p.getLength(), "UTF-8").trim();
                handlePacket(msg);

            } catch (IOException e) {
                System.err.println("Error receiving: " + e.getMessage());
                break;
            }
        }
    }

    private void handlePacket(String packet) {
        String[] parts = packet.split("\\|", 4);
        if (parts.length != 4) {
            System.out.println("[INVALID PACKET] " + packet);
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
            System.out.println("[CORRUPTED MESSAGE DETECTED] " + packet);
            return;
        }

        System.out.println("[" + time + "] " + user + ": " + plainText);
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
}
