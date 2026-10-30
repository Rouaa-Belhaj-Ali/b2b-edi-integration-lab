package b2blab;

/** Runs the scenarios. Usage: Main            (all)    |    Main 2 4     (only scenarios 2 and 4) */
public class Main {
    public static void main(String[] args) throws Exception {
        Services.init();
        int[] only = new int[args.length];
        for (int i = 0; i < args.length; i++) only[i] = Integer.parseInt(args[i]);
        boolean allAsExpected = Scenarios.runAll(only);
        System.exit(allAsExpected ? 0 : 1);
    }
}
