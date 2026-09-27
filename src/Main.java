/**
 * CLI entry point. Right now it only supports what Section 6.2 asks for:
 *
 *   java -cp out Main --tree "project[Name](select[Age>30](Employees))"
 *
 * The operator implementations (select/project/etc actually running against
 * data) come in a later stage of the project -- this is just parsing +
 * printing, so you can see the tree without executing anything.
 */
public final class Main {
    public static void main(String[] args) {
        // The tree printer uses Unicode box-drawing characters (└── ├── │).
        // Some terminals/CI environments default stdout to ASCII, which
        // silently mangles those into '?'. Forcing UTF-8 here makes the
        // output correct regardless of the platform's default console
        // encoding -- discovered by actually running this on a sandbox
        // whose default encoding was ASCII, not by inspection.
        System.setOut(new java.io.PrintStream(System.out, true, java.nio.charset.StandardCharsets.UTF_8));

        if (args.length >= 2 && args[0].equals("--tree")) {
            runTree(args[1]);
            return;
        }
        System.out.println("Usage: java Main --tree \"<query>\"");
    }

    private static void runTree(String query) {
        try {
            Expr tree = new Parser(query).parseQuery();
            System.out.println();
            System.out.print(TreePrinter.print(tree));
        } catch (LexicalException | SyntaxException e) {
            // Errors are reported, never thrown as a raw stack trace (Section 6.3).
            System.out.println("Error: " + e.getMessage());
        }
    }
}
