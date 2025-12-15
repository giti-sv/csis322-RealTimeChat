import java.nio.charset.StandardCharsets;

public class Crypto {
    // IMPORTANT: keep the same key across TCP/UDP/GUI clients + servers
    private static final String SECRET_KEY = "CSIS322-XOR-KEY";

    // Encrypt plaintext -> hex string
    public static String encrypt(String plain) {
        byte[] key = SECRET_KEY.getBytes(StandardCharsets.UTF_8);
        byte[] data = plain.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[data.length];

        for (int i = 0; i < data.length; i++) {
            out[i] = (byte) (data[i] ^ key[i % key.length]);
        }
        return bytesToHex(out);
    }

    // Decrypt hex string -> plaintext
    public static String decrypt(String hexCipher) {
        byte[] cipherBytes = hexToBytes(hexCipher);
        byte[] key = SECRET_KEY.getBytes(StandardCharsets.UTF_8);
        byte[] out = new byte[cipherBytes.length];

        for (int i = 0; i < cipherBytes.length; i++) {
            out[i] = (byte) (cipherBytes[i] ^ key[i % key.length]);
        }
        return new String(out, StandardCharsets.UTF_8);
    }

    // If not valid hex, treat as plaintext (useful for commands or legacy)
    public static String tryDecrypt(String maybeCipher) {
        if (maybeCipher == null)
            return "";
        if (!maybeCipher.matches("(?i)^[0-9a-f]+$") || (maybeCipher.length() % 2 != 0)) {
            return maybeCipher;
        }
        try {
            return decrypt(maybeCipher);
        } catch (Exception e) {
            return maybeCipher;
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes)
            sb.append(String.format("%02x", b));
        return sb.toString();
    }

    private static byte[] hexToBytes(String hex) {
        int n = hex.length() / 2;
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) {
            out[i] = (byte) Integer.parseInt(hex.substring(2 * i, 2 * i + 2), 16);
        }
        return out;
    }
}
