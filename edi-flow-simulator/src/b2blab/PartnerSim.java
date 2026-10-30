package b2blab;

import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

/** The partner side: polls its inbound folder, only ever picks up finished (.snf) files, writes an ack. */
final class PartnerSim {
    static List<String> poll() throws IOException {
        List<String> acks = new ArrayList<>();
        List<Path> files;
        try (var s = Files.list(Env.partnerInbound())) {
            files = s.filter(f -> f.toString().endsWith(".snf")).sorted().toList();
        }
        for (Path f : files) {
            byte[] bytes = Files.readAllBytes(f);
            String ack = "RECEIVED " + f.getFileName() + " bytes=" + bytes.length + " sha256=" + sha256(bytes).substring(0, 12);
            Files.writeString(Env.partnerAcks().resolve("ACK_" + f.getFileName() + ".txt"), ack);
            acks.add(ack);
        }
        return acks;
    }

    private static String sha256(byte[] b) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(b)); }
        catch (Exception e) { throw new IllegalStateException(e); }
    }
}
