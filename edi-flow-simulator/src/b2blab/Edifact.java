package b2blab;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Pattern;

/** Minimal EDIFACT reader (stands in for the EDI module's parse/validate services). */
public final class Edifact {
    public record Segment(String tag, List<List<String>> elements) {
        /** element index (after the tag) and component index; empty string when absent. */
        public String comp(int element, int component) {
            if (element >= elements.size()) return "";
            List<String> e = elements.get(element);
            return component < e.size() ? e.get(component) : "";
        }
    }

    public record Interchange(String sender, String receiver, String controlRef,
                              String messageType, List<Segment> segments) { }

    /** Accepts a String or byte[]. Anything else is the "Unrecognized input data type" case. */
    public static Interchange parse(Object input) throws ServiceException {
        String text;
        if (input instanceof String s) text = s;
        else if (input instanceof byte[] b) text = new String(b, StandardCharsets.UTF_8);
        else throw new ServiceException("Unrecognized input data type [SIM-EDI.000010.000032] (got "
                + (input == null ? "null" : input.getClass().getSimpleName()) + ")");

        text = text.strip();
        char comp = ':', elem = '+', term = '\'';          // release characters are not handled (simulation)
        if (text.startsWith("UNA")) {
            if (text.length() < 9) throw new ServiceException("Invalid data - UNA service string advice is incomplete");
            comp = text.charAt(3); elem = text.charAt(4); term = text.charAt(8);
            text = text.substring(9).strip();
        }
        if (!text.startsWith("UNB")) throw new ServiceException("Invalid data - interchange header (UNB) not found");

        List<Segment> segs = new ArrayList<>();
        for (String raw : text.split(Pattern.quote(String.valueOf(term)))) {
            String seg = raw.strip();
            if (seg.isEmpty()) continue;
            String[] parts = seg.split(Pattern.quote(String.valueOf(elem)), -1);
            List<List<String>> els = new ArrayList<>();
            for (int i = 1; i < parts.length; i++)
                els.add(Arrays.asList(parts[i].split(Pattern.quote(String.valueOf(comp)), -1)));
            segs.add(new Segment(parts[0], els));
        }
        return check(segs);
    }

    private static Interchange check(List<Segment> segs) throws ServiceException {
        Segment unb = segs.get(0), unz = segs.get(segs.size() - 1);
        if (!unz.tag().equals("UNZ")) throw new ServiceException("Invalid data - UNZ trailer missing");
        if (!unz.comp(1, 0).equals(unb.comp(4, 0)))
            throw new ServiceException("Invalid data - UNZ control reference does not match UNB");
        int unh = -1, unt = -1;
        for (int i = 0; i < segs.size(); i++) {
            if (segs.get(i).tag().equals("UNH")) unh = i;
            if (segs.get(i).tag().equals("UNT")) unt = i;
        }
        if (unh < 0 || unt < unh) throw new ServiceException("Invalid data - UNH/UNT pair not found");
        int found = unt - unh + 1;
        int declared = Integer.parseInt(segs.get(unt).comp(0, 0));
        if (declared != found)
            throw new ServiceException("Invalid data - UNT segment count mismatch (declared " + declared + ", found " + found + ")");
        return new Interchange(unb.comp(1, 0), unb.comp(2, 0), unb.comp(4, 0), segs.get(unh).comp(1, 0), segs);
    }
}
