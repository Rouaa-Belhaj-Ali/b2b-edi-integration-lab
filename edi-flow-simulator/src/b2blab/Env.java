package b2blab;

import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;

/** The simulated environment: where files go, and a knob to inject transport failures. */
final class Env {
    static final Path ROOT = Paths.get("sim-data");
    static int injectedFailures = 0;

    static Path partnerInbound() { return ROOT.resolve("partner-sftp").resolve("inbound"); }
    static Path partnerAcks() { return ROOT.resolve("partner-sftp").resolve("acks"); }

    static void reset() throws IOException {
        if (Files.exists(ROOT)) {
            try (var walk = Files.walk(ROOT)) {
                walk.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
        Files.createDirectories(partnerInbound());
        Files.createDirectories(partnerAcks());
        injectedFailures = 0;
        TN.reset();
        PipelineStore.reset();
    }
}
