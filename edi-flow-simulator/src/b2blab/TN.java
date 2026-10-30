package b2blab;

import java.util.*;
import java.util.regex.*;

/** Stand-in for the trading-network layer: recognizes a document, stores it, routes it, logs it. */
final class TN {
    static final Map<String, BizDoc> DOCS = new LinkedHashMap<>();
    static final List<String> ACTIVITY = new ArrayList<>();
    private static int seq = 0;

    static void reset() { DOCS.clear(); ACTIVITY.clear(); seq = 0; }

    static void log(String msg) { ACTIVITY.add(msg); }

    /** Document recognition: look at the content and decide what kind of document this is. */
    static BizDoc receive(String raw) {
        String type = raw.startsWith("UNB") && raw.contains("ORDRSP:1:921") ? "ORDRSP" : "Unknown";
        Matcher m = Pattern.compile("^UNB\\+[^+]*\\+([^:+]*)[^+]*\\+([^:+]*)").matcher(raw);
        boolean found = m.find();
        String sender = found ? m.group(1) : "?";
        String receiver = found ? m.group(2) : "?";
        BizDoc d = new BizDoc(String.format("BD-%04d", ++seq), type, sender, receiver);
        d.content.put("edidata", raw);
        DOCS.put(d.id, d);
        log("received " + d.id + " type=" + type + " from " + sender);
        return d;
    }

    /** Processing rule: ORDRSP documents are handed to the ORDRSP flow service. */
    static ProcessOrdrsp.Result route(BizDoc d, String user) {
        if (!d.docType.equals("ORDRSP")) {
            setStatus(d, "NO_RULE", "no processing rule for " + d.docType);
            return new ProcessOrdrsp.Result(false, "NO_RULE", "no processing rule", null, new Pipeline());
        }
        d.status = "PROCESSING";
        return ProcessOrdrsp.run(new Pipeline().put("bizdoc", d).put("user", user), ProcessOrdrsp.STEPS);
    }

    static void setStatus(BizDoc d, String status, String note) {
        d.status = status;
        log(d.id + " -> " + status + (note == null ? "" : " (" + note + ")"));
    }
}
