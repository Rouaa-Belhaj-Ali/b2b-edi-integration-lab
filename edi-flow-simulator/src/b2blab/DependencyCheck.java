package b2blab;

import java.util.*;

/** Static check for a copied package: does any step still call a service from another package? */
final class DependencyCheck {
    private static final List<String> SHARED = List.of("pub.", "wm.", "edi.");

    static List<String> foreignReferences(List<String> steps, String ownPrefix) {
        List<String> out = new ArrayList<>();
        for (String s : steps)
            if (!s.startsWith(ownPrefix) && SHARED.stream().noneMatch(s::startsWith)) out.add(s);
        return out;
    }
}
