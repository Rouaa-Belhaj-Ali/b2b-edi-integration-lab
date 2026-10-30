package b2blab;

import java.io.IOException;
import java.util.*;

/** The flow services. Names follow the "package.folder:service" style of the server namespace. */
final class Services {
    // partner profile tables (stand-in for the database lookup behind get_TP_ID)
    static final Map<String, String> TP_TABLE = Map.of("SELLERX", "TP-0001", "SELLERZ", "TP-0002");
    static final Map<String, String> ORIG_TP_TABLE = Map.of("SELLERX", "TP-9999");   // the ORIGINAL package's data

    static void init() {
        Registry.register("pub.flow:clearPipeline", Services::clearPipeline);
        Registry.register("edi.handler:validateTransaction", Services::validateTransaction);
        Registry.register("edi.tn:getEnvDocFromBizdoc", Services::getEnvDocFromBizdoc);
        Registry.register("edi.core:getICDetail", Services::getICDetail);
        Registry.register("ordrsp.service:get_TP_ID", p -> getTpId(p, TP_TABLE));
        Registry.register("ordrspOrig.service:get_TP_ID", p -> getTpId(p, ORIG_TP_TABLE));
        Registry.register("ordrsp.service:getDeliveryDate", Services::getDeliveryDate);
        Registry.register("ordrsp.map:inboundMap", p -> inboundMap(p, null));
        Registry.register("ordrsp.map:inboundMap_copy", p -> inboundMap(p, "TP-0001"));   // locked value left in a copy
        Registry.register("ordrsp.service:Generate_SNF", Services::generateSnf);
        Registry.register("ordrsp.service:validateOutput", Services::validateOutput);
        Registry.register("ordrsp.delivery:sendFile", Services::sendFile);
        Registry.register("ordrsp.tn:updateBizdocStatus", Services::updateBizdocStatus);
        Registry.register("ordrsp.util:logEDIRecord", Services::logEdiRecord);
    }

    /** Keeps only a few variables. Anything a later step needs must be in this list. */
    private static void clearPipeline(Pipeline p) {
        Set<String> keep = Set.of("bizdoc", "user", "traceFlag");
        for (String k : p.keys()) if (!keep.contains(k)) p.remove(k);
    }

    private static void validateTransaction(Pipeline p) throws ServiceException {
        Registry.invoke("edi.tn:getEnvDocFromBizdoc", p);
        Registry.invoke("edi.core:getICDetail", p);
        Edifact.Interchange ic = (Edifact.Interchange) p.get("interchange");
        if (!"ORDRSP".equals(ic.messageType()))
            throw new ServiceException("validateTransaction: expected ORDRSP but got " + ic.messageType());
    }

    /** Takes the EDI content part out of the bizdoc. Needs a real bizdoc in the pipeline. */
    private static void getEnvDocFromBizdoc(Pipeline p) throws ServiceException {
        if (!(p.get("bizdoc") instanceof BizDoc d))
            throw new ServiceException("getEnvDocFromBizdoc: no bizdoc in the pipeline (this service expects one from TN)");
        p.put("edidata", d.content.get("edidata"));
    }

    /** Parses the interchange. The error everyone sees lives here, but the cause is usually upstream. */
    private static void getICDetail(Pipeline p) throws ServiceException {
        p.put("interchange", Edifact.parse(p.get("edidata")));
    }

    private static void getTpId(Pipeline p, Map<String, String> table) throws ServiceException {
        Edifact.Interchange ic = (Edifact.Interchange) p.get("interchange");
        String tp = table.get(ic.sender());
        if (tp == null) throw new ServiceException("get_TP_ID: no partner profile for sender " + ic.sender());
        p.put("tpId", tp);
    }

    /** Current schedule date (DTM 67) wins over the requested date (DTM 2). */
    private static void getDeliveryDate(Pipeline p) throws ServiceException {
        Edifact.Interchange ic = (Edifact.Interchange) p.get("interchange");
        Map<String, String> dates = new LinkedHashMap<>();
        String line = null, requested = null, current = null;
        for (Edifact.Segment s : ic.segments()) {
            switch (s.tag()) {
                case "LIN" -> {
                    if (line != null) dates.put(line, pick(current, requested, line));
                    line = s.comp(0, 0); requested = null; current = null;
                }
                case "DTM" -> {
                    String q = s.comp(0, 0);
                    if (q.equals("2")) requested = s.comp(0, 1);
                    if (q.equals("67")) current = s.comp(0, 1);
                }
                default -> { }
            }
        }
        if (line != null) dates.put(line, pick(current, requested, line));
        p.put("deliveryDates", dates);
    }

    private static String pick(String current, String requested, String line) throws ServiceException {
        if (current != null) return current;
        if (requested != null) return requested;
        throw new ServiceException("getDeliveryDate: no delivery date for line " + line);
    }

    /** EDIFACT segments -> internal document. lockedTpId simulates a constant left in a copied map. */
    @SuppressWarnings("unchecked")
    private static void inboundMap(Pipeline p, String lockedTpId) throws ServiceException {
        Edifact.Interchange ic = (Edifact.Interchange) p.get("interchange");
        Map<String, String> dates = (Map<String, String>) p.get("deliveryDates");
        Map<String, Object> doc = new LinkedHashMap<>();
        List<Map<String, String>> lines = new ArrayList<>();
        Map<String, String> cur = null;
        boolean inSchedule = false;
        for (Edifact.Segment s : ic.segments()) {
            switch (s.tag()) {
                case "BGM" -> doc.put("docNo", s.comp(1, 0));
                case "DTM" -> { if (s.comp(0, 0).equals("137")) doc.put("msgDate", s.comp(0, 1)); }
                case "RFF" -> { if (s.comp(0, 0).equals("OP")) doc.put("orderNo", s.comp(0, 1)); }
                case "LIN" -> {
                    cur = new LinkedHashMap<>();
                    inSchedule = false;
                    cur.put("line", s.comp(0, 0));
                    cur.put("part", s.comp(2, 0));
                    cur.put("date", dates.get(s.comp(0, 0)));
                    lines.add(cur);
                }
                case "SCC" -> inSchedule = true;
                case "QTY" -> { if (cur != null && !inSchedule && s.comp(0, 0).equals("113")) cur.put("qty", s.comp(0, 1)); }
                default -> { }
            }
        }
        doc.put("tpId", lockedTpId != null ? lockedTpId : p.str("tpId"));
        doc.put("lines", lines);
        p.put("ordrspDoc", doc);
    }

    /** Internal document -> fixed-width flat file (illustrative layout, not a real partner format). */
    @SuppressWarnings("unchecked")
    private static void generateSnf(Pipeline p) throws ServiceException {
        Map<String, Object> doc = (Map<String, Object>) p.get("ordrspDoc");
        List<Map<String, String>> lines = (List<Map<String, String>>) doc.get("lines");
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("H%-10s%-15s%-15s%-8s", doc.get("tpId"), doc.get("docNo"), doc.get("orderNo"), doc.get("msgDate"))).append("\n");
        for (Map<String, String> l : lines) {
            if (l.get("qty") == null) throw new ServiceException("Generate_SNF: no quantity for line " + l.get("line"));
            sb.append(String.format("D%04d%-20s%010d%-8s", Integer.parseInt(l.get("line")), l.get("part"),
                    Integer.parseInt(l.get("qty")), l.get("date"))).append("\n");
        }
        sb.append(String.format("T%06d", lines.size()));
        p.put("snf", sb.toString());
        p.put("snfFileName", "ORDRSP_" + doc.get("tpId") + "_" + doc.get("docNo") + ".snf");
    }

    /** Guard before sending: check the output against what we know, not only that it "looks fine". */
    private static void validateOutput(Pipeline p) throws ServiceException {
        String[] rows = p.str("snf").split("\n");
        String headerTp = rows[0].substring(1, 11).trim();
        if (!headerTp.equals(p.str("tpId")))
            throw new ServiceException("validateOutput: SNF header tpId " + headerTp + " does not match the partner lookup " + p.str("tpId"));
        long details = Arrays.stream(rows).filter(r -> r.startsWith("D")).count();
        long declared = Long.parseLong(rows[rows.length - 1].substring(1).trim());
        if (details != declared) throw new ServiceException("validateOutput: trailer says " + declared + " rows but file has " + details);
    }

    /** REPEAT-style retry around the file transfer. */
    private static void sendFile(Pipeline p) throws ServiceException {
        String name = p.str("snfFileName");
        int max = p.has("maxAttempts") ? Integer.parseInt(p.str("maxAttempts")) : 3;
        for (int attempt = 1; ; attempt++) {
            try {
                FileTransport.send(name, p.str("snf"));
                p.put("deliveredAs", name);
                p.put("attempts", attempt);
                return;
            } catch (IOException e) {
                Flow.say("  send attempt " + attempt + " failed: " + e.getMessage());
                if (attempt >= max) {
                    FileTransport.cleanup(name);   // never leave a half-written file behind
                    throw new ServiceException("sendFile: giving up after " + attempt + " attempts (" + e.getMessage() + ")");
                }
            }
        }
    }

    private static void updateBizdocStatus(Pipeline p) {
        TN.setStatus((BizDoc) p.get("bizdoc"), "DONE", "delivered as " + p.str("deliveredAs"));
    }

    private static void logEdiRecord(Pipeline p) {
        BizDoc d = (BizDoc) p.get("bizdoc");
        TN.log("EDI record: " + d.id + " tp=" + p.str("tpId") + " file=" + p.str("snfFileName"));
    }
}
