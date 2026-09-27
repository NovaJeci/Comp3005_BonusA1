import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * CLI entry point.
 *
 *   java Main --tree "<query>"                      print the parse tree only
 *   java Main --run <relationsFile> "<query>"        load relations, run the query, print the result
 *
 * Both modes catch every exception type this project defines and print a
 * clean message -- never a raw Java stack trace (Section 6.3).
 */
public final class Main {
    public static void main(String[] args) {
        // The tree printer uses Unicode box-drawing characters (└── ├── │). Some
        // terminals/CI environments default stdout to ASCII, which silently mangles
        // those into '?' -- found by actually running this, not by inspection.
        System.setOut(new java.io.PrintStream(System.out, true, StandardCharsets.UTF_8));

        if (args.length >= 2 && args[0].equals("--tree")) {
            runTree(args[1]);
            return;
        }
        if (args.length >= 3 && args[0].equals("--run")) {
            runQuery(args[1], args[2]);
            return;
        }
        System.out.println("Usage:");
        System.out.println("  java Main --tree \"<query>\"");
        System.out.println("  java Main --run <relationsFile> \"<query>\"");
    }

    private static void runTree(String query) {
        try {
            Expr tree = new Parser(query).parseQuery();
            System.out.println();
            System.out.print(TreePrinter.print(tree));
        } catch (LexicalException | SyntaxException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private static void runQuery(String relationsFile, String query) {
        try {
            String source = Files.readString(Path.of(relationsFile), StandardCharsets.UTF_8);
            var relations = RelationLoader.load(source);
            Expr tree = new Parser(query).parseQuery();
            Evaluator evaluator = new Evaluator(relations);
            Relation result = evaluator.evaluate(tree);
            System.out.println();
            System.out.print(ResultPrinter.print(result));
            System.out.println();
            System.out.println("(select comparisons: " + evaluator.selectComparisons
                    + ", join comparisons: " + evaluator.joinComparisons + ")");
        } catch (IOException e) {
            System.out.println("Error: could not read '" + relationsFile + "': " + e.getMessage());
        } catch (LexicalException | SyntaxException | EvalException e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}
