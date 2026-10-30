package b2blab;

import java.util.*;

/** Stand-in for the flow pipeline (IData): named variables passed from step to step. */
public class Pipeline {
    private final Map<String, Object> data = new LinkedHashMap<>();

    public Pipeline put(String key, Object value) { data.put(key, value); return this; }
    public Object get(String key) { return data.get(key); }
    public boolean has(String key) { return data.containsKey(key); }
    public String str(String key) { Object o = data.get(key); return o == null ? null : o.toString(); }
    public void remove(String key) { data.remove(key); }
    public Set<String> keys() { return new LinkedHashSet<>(data.keySet()); }
    public void putAll(Pipeline other) { data.putAll(other.data); }
    public Pipeline copy() { Pipeline c = new Pipeline(); c.data.putAll(data); return c; }

    /** Like the Pipeline panel in a debugger: name, type, value (shortened). */
    public String dump() {
        if (data.isEmpty()) return "      (pipeline is empty)";
        StringBuilder sb = new StringBuilder();
        for (var e : new TreeMap<>(data).entrySet()) {
            Object v = e.getValue();
            String type = v == null ? "null" : v.getClass().getSimpleName();
            String text = String.valueOf(v).replace("\n", " | ");
            if (text.length() > 58) text = text.substring(0, 58) + "...";
            sb.append(String.format("      %-14s %-12s %s%n", e.getKey(), type, text));
        }
        return sb.toString().stripTrailing();
    }
}
