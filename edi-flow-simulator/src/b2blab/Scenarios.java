package b2blab;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Nine scenarios. Each prints what happened and checks it against what a developer should expect. */
final class Scenarios {
    private record Outcome(int n, String title, boolean pass) { }
    private static final List<Outcome> OUTCOMES = new ArrayList<>();
    private static final String[] TITLES = {
        "Happy path: partner ORDRSP -> map -> flat file -> file delivery -> partner ack",
        "Error deep in the chain: the parser receives something that is not raw EDI",
        "Entry-point service run directly, without a TN bizdoc (empty pipeline)",
        "Debug shortcut restores another developer's pipeline",
        "Copied package still calls a service from the original package",
        "Locked value left in a copied map (silent wrong output vs. output validation)",
        "Unknown trading partner",
        "Partner sends a message with a wrong segment count",
        "File delivery: transient failure (retry) and permanent failure (no partial file)"
    };

    static boolean runAll(int[] only) throws IOException {
        Set<Integer> wanted = new HashSet<>();
        for (int n : only) wanted.add(n);
        for (int n = 1; n <= 9; n++) if (wanted.isEmpty() || wanted.contains(n)) run(n);
        System.out.println("\n=== Summary ===");
        boolean all = true;
        for (Outcome o : OUTCOMES) {
            System.out.printf("  %s  Scenario %d: %s%n", o.pass() ? "PASS" : "FAIL", o.n(), o.title());
            all &= o.pass();
        }
        return all;
    }

    private static void run(int n) throws IOException {
        System.out.println("\n=== Scenario " + n + ": " + TITLES[n - 1] + " ===");
        Env.reset();
        switch (n) {
            case 1 -> s1(); case 2 -> s2(); case 3 -> s3(); case 4 -> s4(); case 5 -> s5();
            case 6 -> s6(); case 7 -> s7(); case 8 -> s8(); case 9 -> s9();
            default -> throw new IllegalArgumentException("No scenario " + n);
        }
    }

    // ---- helpers -------------------------------------------------------------------------------

    private static String sample() throws IOException {
        return Files.readString(Paths.get("samples", "ORDRSP_sample.edi")).strip();
    }

    private static void done(int n, boolean pass, String lesson) {
        System.out.println("  LESSON: " + lesson);
        System.out.println("  -> " + (pass ? "behaved as expected" : "UNEXPECTED RESULT"));
        OUTCOMES.add(new Outcome(n, TITLES[n - 1], pass));
    }

    private static void show(ProcessOrdrsp.Result r) {
        if (r.ok()) {
            String st = r.pipeline().get("bizdoc") instanceof BizDoc d ? d.status : "-";
            System.out.println("  RESULT: OK, bizdoc status " + st);
        } else {
            System.out.println("  RESULT: FAILED -> " + r.error());
            if (r.ex() != null) {
                System.out.println("  call chain (innermost first): " + String.join(" <- ", r.ex().callChain()));
                System.out.println("  pipeline as the failing step saw it:");
                System.out.println(r.ex().pipelineSnapshot());
            }
        }
    }

    private static List<String> stepsWith(String replace, String by) {
        List<String> s = new ArrayList<>(ProcessOrdrsp.STEPS);
        s.set(s.indexOf(replace), by);
        return s;
    }

    private static ProcessOrdrsp.Result runSteps(BizDoc d, String user, List<String> steps) {
        d.status = "PROCESSING";
        return ProcessOrdrsp.run(new Pipeline().put("bizdoc", d).put("user", user), steps);
    }

    // ---- scenarios -----------------------------------------------------------------------------

    private static void s1() throws IOException {
        BizDoc d = TN.receive(sample());
        ProcessOrdrsp.Result r = TN.route(d, "dev1");
        show(r);
        r.pipeline().str("snf").lines().forEach(l -> System.out.println("      | " + l));
        List<String> acks = PartnerSim.poll();
        acks.forEach(a -> System.out.println("  partner: " + a));
        TN.ACTIVITY.forEach(a -> System.out.println("  TN log: " + a));
        done(1, r.ok() && acks.size() == 1 && d.status.equals("DONE"),
                "Know the healthy path first. Every failure is a deviation from it.");
    }

    private static void s2() throws IOException {
        BizDoc d = TN.receive(sample());
        d.content.put("edidata", new LinkedHashMap<>(Map.of("note", "already-converted document, not raw EDI")));
        ProcessOrdrsp.Result r = TN.route(d, "dev1");
        show(r);
        boolean pass = !r.ok() && r.error().startsWith("Unrecognized input data type")
                && r.ex().callChain().contains("edi.core:getICDetail");
        done(2, pass, "The error is raised at the parser, but the wrong thing was put there earlier. "
                + "Check the INPUT variable of the failing step (name, type, content) before touching anything else.");
    }

    private static void s3() {
        ProcessOrdrsp.Result r = ProcessOrdrsp.run(new Pipeline(), ProcessOrdrsp.STEPS);
        show(r);
        done(3, !r.ok() && r.error().contains("no bizdoc"),
                "A service designed to be triggered by the trading-network layer has no input window. "
                + "Replay a real document through the trigger instead of running it bare.");
    }

    private static void s4() throws IOException {
        BizDoc a = TN.receive(sample());                                     // alice's document
        BizDoc b = TN.receive(sample().replace("SELLERX", "SELLERZ"));      // bob's document
        Flow.trace = false;
        TN.route(a, "alice");
        TN.route(b, "bob");                                                  // overwrites the shared slot
        Flow.trace = true;
        System.out.println("  alice debugs HER document " + a.id + " with traceFlag set:");
        ProcessOrdrsp.Result r = ProcessOrdrsp.run(new Pipeline().put("user", "alice").put("traceFlag", "1"), ProcessOrdrsp.STEPS);
        BizDoc seen = (BizDoc) r.pipeline().get("bizdoc");
        boolean contaminated = seen != null && seen.id.equals(b.id) && "bob".equals(r.pipeline().str("user"));
        System.out.println("  -> the restore loaded " + (seen == null ? "nothing" : seen.id) + " owned by " + r.pipeline().str("user"));

        PipelineStore.mode = PipelineStore.Mode.PER_USER;                    // the fix: one slot per developer
        Flow.trace = false;
        TN.route(a, "alice");
        TN.route(b, "bob");
        Flow.trace = true;
        System.out.println("  after the fix (one slot per user):");
        ProcessOrdrsp.Result r2 = ProcessOrdrsp.run(new Pipeline().put("user", "alice").put("traceFlag", "1"), ProcessOrdrsp.STEPS);
        BizDoc seen2 = (BizDoc) r2.pipeline().get("bizdoc");
        boolean fixed = seen2 != null && seen2.id.equals(a.id);
        done(4, contaminated && fixed, "Anything shared (files, caches, 'last run' data) can hold someone else's data. "
                + "Key debug state per person, and always look at what was actually restored.");
    }

    private static void s5() throws IOException {
        List<String> copied = stepsWith("ordrsp.service:get_TP_ID", "ordrspOrig.service:get_TP_ID");
        List<String> offenders = DependencyCheck.foreignReferences(copied, "ordrsp.");
        System.out.println("  dependency check on the copied flow: " + (offenders.isEmpty() ? "clean" : "FOREIGN REFERENCES " + offenders));
        BizDoc d = TN.receive(sample());
        ProcessOrdrsp.Result r = runSteps(d, "dev1", copied);
        show(r);
        System.out.println("  file delivered as: " + r.pipeline().str("deliveredAs") + "   (the partner should be TP-0001)");
        done(5, offenders.size() == 1 && r.ok() && r.pipeline().str("deliveredAs").contains("TP-9999"),
                "A copied package can run perfectly and still use the original's data. Scan references after every copy; "
                + "this kind of error does not fail, it ships.");
    }

    private static void s6() throws IOException {
        List<String> noCheck = new ArrayList<>(stepsWith("ordrsp.map:inboundMap", "ordrsp.map:inboundMap_copy"));
        noCheck.remove("ordrsp.service:validateOutput");
        BizDoc d1 = TN.receive(sample().replace("SELLERX", "SELLERZ"));
        System.out.println("  a) copied map, no output validation (partner should be TP-0002):");
        ProcessOrdrsp.Result r1 = runSteps(d1, "dev1", noCheck);
        String header = r1.pipeline().str("snf") == null ? "" : r1.pipeline().str("snf").lines().findFirst().orElse("");
        System.out.println("     header sent: " + header.stripTrailing());
        boolean wentOut = r1.ok() && header.contains("TP-0001");

        Env.reset();
        BizDoc d2 = TN.receive(sample().replace("SELLERX", "SELLERZ"));
        System.out.println("  b) same copied map, with output validation:");
        ProcessOrdrsp.Result r2 = runSteps(d2, "dev1", stepsWith("ordrsp.map:inboundMap", "ordrsp.map:inboundMap_copy"));
        show(r2);
        done(6, wentOut && !r2.ok() && r2.error().contains("tpId"),
                "A linked or hard-coded value copied from someone else's map locks the field. Validate the output "
                + "against the lookup result, so wrong data stops before it leaves.");
    }

    private static void s7() throws IOException {
        BizDoc d = TN.receive(sample().replace("SELLERX", "SELLERQ"));
        ProcessOrdrsp.Result r = TN.route(d, "dev1");
        show(r);
        boolean nothingSent = PartnerSim.poll().isEmpty();
        done(7, !r.ok() && r.error().contains("no partner profile") && d.status.equals("ERROR") && nothingSent,
                "Fail loudly and early: a clear message, an ERROR status on the document, and nothing sent.");
    }

    private static void s8() throws IOException {
        BizDoc d = TN.receive(sample().replaceAll("UNT\\+\\d+\\+1", "UNT+22+1"));
        ProcessOrdrsp.Result r = TN.route(d, "dev1");
        show(r);
        done(8, !r.ok() && r.error().contains("segment count mismatch"),
                "One wrong number in a trailer rejects the whole message. Read the partner's guideline and "
                + "compare against the raw file, character by character.");
    }

    private static void s9() throws IOException {
        Env.injectedFailures = 2;
        BizDoc d = TN.receive(sample());
        System.out.println("  a) two transient failures, three attempts allowed:");
        ProcessOrdrsp.Result r = TN.route(d, "dev1");
        show(r);
        List<String> acks = PartnerSim.poll();
        acks.forEach(x -> System.out.println("  partner: " + x));
        boolean retryOk = r.ok() && "3".equals(r.pipeline().str("attempts")) && acks.size() == 1;

        Env.reset();
        Env.injectedFailures = 5;
        BizDoc d2 = TN.receive(sample());
        System.out.println("  b) the connection never comes back:");
        ProcessOrdrsp.Result r2 = TN.route(d2, "dev1");
        show(r2);
        List<Path> leftovers;
        try (var s = Files.list(Env.partnerInbound())) { leftovers = s.toList(); }
        System.out.println("  files left in the partner's inbound folder: " + leftovers.size());
        done(9, retryOk && !r2.ok() && d2.status.equals("ERROR") && leftovers.isEmpty(),
                "Write under a temporary name and rename when complete, retry with a limit, and clean up. "
                + "The partner must never see a half-written file.");
    }
}
