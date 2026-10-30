package b2blab;

/** Runs one service and traces it, like the Results panel plus the debugger call stack. */
public final class Flow {
    public interface Step { void run(Pipeline p) throws ServiceException; }

    static boolean trace = true;
    private static int depth = 0;

    static void call(String name, Pipeline p, Step step) throws ServiceException {
        say(">> " + name);
        depth++;
        try {
            step.run(p);
        } catch (ServiceException e) {
            e.addFrame(name, p);
            say("!! " + name + " failed");
            throw e;
        } finally {
            depth--;
        }
    }

    static void say(String msg) {
        if (trace) System.out.println("    " + "  ".repeat(depth) + msg);
    }
}
