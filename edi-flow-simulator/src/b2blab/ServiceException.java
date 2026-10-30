package b2blab;

import java.util.*;

/** Stands in for a flow-service exception. Collects the call chain while it bubbles up. */
public class ServiceException extends Exception {
    private static final long serialVersionUID = 1L;
    private final List<String> callChain = new ArrayList<>();
    private String pipelineSnapshot;

    public ServiceException(String message) { super(message); }

    /** Called by Flow for every service the error passes through (innermost first). */
    void addFrame(String service, Pipeline p) {
        callChain.add(service);
        if (pipelineSnapshot == null) pipelineSnapshot = p.dump();   // pipeline as the FAILING step saw it
    }

    public List<String> callChain() { return callChain; }
    public String pipelineSnapshot() { return pipelineSnapshot == null ? "      (none)" : pipelineSnapshot; }
}
