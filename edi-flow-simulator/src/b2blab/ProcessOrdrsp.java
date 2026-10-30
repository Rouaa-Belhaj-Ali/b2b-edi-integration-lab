package b2blab;

import java.util.*;

/** The entry-point flow service: branch on traceFlag, then Try (steps) / Catch (status + log). */
final class ProcessOrdrsp {
    record Result(boolean ok, String status, String error, ServiceException ex, Pipeline pipeline) { }

    static final String NAME = "ordrsp.handler:processORDRSP";

    /** The Try sequence, as data, so a "copied package" can be modelled by editing the list. */
    static final List<String> STEPS = List.of(
            "pub.flow:clearPipeline",
            "edi.handler:validateTransaction",
            "ordrsp.service:get_TP_ID",
            "ordrsp.service:getDeliveryDate",
            "ordrsp.map:inboundMap",
            "ordrsp.service:Generate_SNF",
            "ordrsp.service:validateOutput",
            "ordrsp.delivery:sendFile",
            "ordrsp.tn:updateBizdocStatus",
            "ordrsp.util:logEDIRecord");

    static Result run(Pipeline p, List<String> steps) {
        Flow.say("== " + NAME + "  (user=" + p.str("user") + ", traceFlag=" + p.str("traceFlag") + ")");
        try {
            if (p.get("traceFlag") == null) {              // BRANCH on /traceFlag: $null
                PipelineStore.save(NAME, p);
                Flow.say("[branch] traceFlag empty -> pipeline SAVED");
            } else {                                        // $default
                PipelineStore.restore(NAME, p);
                Flow.say("[branch] traceFlag set   -> pipeline RESTORED");
            }
            for (String step : steps) Registry.invoke(step, p);
            return new Result(true, "DONE", null, null, p);
        } catch (ServiceException e) {                      // SEQUENCE (Catch)
            e.addFrame(NAME, p);
            if (p.get("bizdoc") instanceof BizDoc d) TN.setStatus(d, "ERROR", e.getMessage());
            return new Result(false, "ERROR", e.getMessage(), e, p);
        }
    }
}
