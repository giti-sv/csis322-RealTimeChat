import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class History {

    private static Path ensureHistoryDir() throws Exception {
        Path dir = Paths.get("history");
        if (!Files.exists(dir))
            Files.createDirectories(dir);
        return dir;
    }

    private static String safe(String s) {
        return s == null ? "null" : s.replaceAll("[^a-zA-Z0-9._-]", "_");
    }

    public static String fileName(String username, String mode, String host, int port) {
        return safe(username) + "_" + safe(mode) + "_" + safe(host) + "_" + port + ".log";
    }

    public static List<String> load(String username, String mode, String host, int port) {
        try {
            Path dir = ensureHistoryDir();
            Path file = dir.resolve(fileName(username, mode, host, port));
            if (!Files.exists(file))
                return new ArrayList<>();
            return Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    public static void append(String username, String mode, String host, int port, String line) {
        try {
            Path dir = ensureHistoryDir();
            Path file = dir.resolve(fileName(username, mode, host, port));
            Files.write(file, Arrays.asList(line), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception ignored) {
        }
    }

    public static void clear(String username, String mode, String host, int port) {
        try {
            Path dir = ensureHistoryDir();
            Path file = dir.resolve(fileName(username, mode, host, port));
            if (Files.exists(file))
                Files.delete(file);
        } catch (Exception ignored) {
        }
    }
}
