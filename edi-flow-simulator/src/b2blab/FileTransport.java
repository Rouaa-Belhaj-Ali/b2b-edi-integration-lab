package b2blab;

import java.io.IOException;
import java.nio.file.*;

/** Simulated file protocol (SFTP-style drop): write under a temporary name, then rename. */
final class FileTransport {
    static void send(String name, String content) throws IOException {
        Path dir = Env.partnerInbound();
        Files.createDirectories(dir);
        Path tmp = dir.resolve(name + ".part");
        Path dst = dir.resolve(name);
        if (Env.injectedFailures > 0) {
            Env.injectedFailures--;
            Files.writeString(tmp, content.substring(0, content.length() / 2));   // connection dropped half-way
            throw new IOException("connection reset by peer");
        }
        Files.writeString(tmp, content);
        Files.move(tmp, dst, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    static void cleanup(String name) {
        try { Files.deleteIfExists(Env.partnerInbound().resolve(name + ".part")); } catch (IOException ignored) { }
    }
}
