package b2blab;

import java.util.*;

/** The server's service namespace: "package.folder:service" -> implementation. */
public final class Registry {
    private static final Map<String, Flow.Step> SERVICES = new LinkedHashMap<>();

    static void register(String name, Flow.Step step) { SERVICES.put(name, step); }

    static void invoke(String name, Pipeline p) throws ServiceException {
        Flow.Step s = SERVICES.get(name);
        if (s == null) throw new ServiceException("Service not found: " + name);
        Flow.call(name, p, s);
    }
}
