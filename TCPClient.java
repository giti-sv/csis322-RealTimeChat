import java.io.*;
import java.net.Socket;
import java.security.MessageDigest;
import java.util.Scanner;

/**
 * CSIS322 Real-Time Chat Application - TCP Client (Console)
 *
 * Expects packets from server:
 * user|time|cipherHex|hash( user|time|plainText )
 *
 * Sends:
 * - username (first line, plaintext)
 * - commands (start with '/', plaintext)
 * - normal messages encrypted (Crypto.encrypt)
 */
public class TCPClient {

    private static final String HOST = "127.0.0.1";
    private static final int PORT = 12345;

    private Socket socket;
    private BufferedReader in;
    private PrintWriter out;

    public static void main(String[] args) {
        new TCPClient().start();
    }

    public void start() {
        try (Scanner sc = new Scanner(System.in)) {
            socket = new Socket(HOST, PORT);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), "UTF-8"));
            out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), "UTF-8"), true);

            System.out.print("Enter your username: ");
            String username = sc.nextLine().trim();
            if (username.isEmpty())
                username = "User";

            out.println(username);

            Thread reader = new Thread(() -> {
                try {
                    String line;
                    while ((line = in.readLine()) != null) {
                        verifyAndDisplay(line);
                    }
                } catch (IOException e) {
                    System.out.println("Disconnected from server.");
                }
            });
            reader.setDaemon(true);
            reader.start();

            while (true) {
                String input = sc.nextLine();
                if (input == null)
                    break;
                input = input.trim();
                if (input.isEmpty())
                    continue;

                if (input.startsWith("/")) {
                    out.println(input);
                    if (input.equalsIgnoreCase("/quit"))
                        break;
                } else {
                    out.println(Crypto.encrypt(input));
                }
            }

        } catch (IOException e) {
            System.err.println("TCP Client error: " + e.getMessage());
        } finally {
            try {
                if (socket != null)
                    socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private void verifyAndDisplay(String packet) {
        try {
            String[] parts = packet.split("\\|", 4);
            if (parts.length != 4) {
                System.out.println("[NON-PACKET] " + packet);
                return;
            }

            String user = parts[0];
            String time = parts[1];
            String encText = parts[2];
            String receivedHash = parts[3];

            String plainText = Crypto.tryDecrypt(encText);

            String base = user + "|" + time + "|" + plainText;
            String computedHash = sha256(base);

            if (!computedHash.equals(receivedHash)) {
                System.out.println("[CORRUPTED MESSAGE DETECTED] " + packet);
                return;
            }

            System.out.println("[" + time + "] " + user + ": " + plainText);

        } catch (Exception e) {
            System.out.println("[ERROR parsing packet] " + packet);
        }
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
