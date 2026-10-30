package b2blab;

import java.util.*;

/** Stand-in for save/restore-pipeline-to-file: a debugging shortcut that replays a saved pipeline. */
final class PipelineStore {
    enum Mode { SHARED_SLOT, PER_USER }
    static Mode mode = Mode.SHARED_SLOT;
    private static final Map<String, Pipeline> SLOTS = new HashMap<>();

    static void reset() { SLOTS.clear(); mode = Mode.SHARED_SLOT; }

    private static String key(String service, Pipeline p) {
        return mode == Mode.PER_USER ? service + "@" + p.str("user") : service;
    }

    static void save(String service, Pipeline p) { SLOTS.put(key(service, p), p.copy()); }

    static void restore(String service, Pipeline p) throws ServiceException {
        Pipeline saved = SLOTS.get(key(service, p));
        if (saved == null) throw new ServiceException("restore: no saved pipeline for " + key(service, p));
        p.putAll(saved);   // note: this also overwrites 'user', 'bizdoc' ... with whatever was saved
    }
}
