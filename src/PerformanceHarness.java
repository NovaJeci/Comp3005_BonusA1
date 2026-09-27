import java.util.Map;

/**
 * Automates Section 8.3: runs R join[R.b=S.b] S at increasing sizes and
 * prints a Markdown table in the shape REPORT.md needs. Uses
 * DataGenerator.generateRelations() directly (no file I/O, no text
 * parsing) so the timed region measures the join itself, not disk or
 * tokenizing overhead.
 *
 * Usage: java PerformanceHarness [matchRate]     (matchRate defaults to 1.0)
 *
 * IMPORTANT: the numbers this prints depend entirely on the machine it
 * runs on. Run it yourself, on your own machine, for the numbers that
 * actually go in REPORT.md -- and record your machine/JVM info (Section
 * 8.4 asks for this) right next to the table. 64000x64000 is 4.096
 * billion comparisons; expect this to take a real amount of time (that's
 * the point -- Section 1 warns you the weaknesses become visible once you
 * actually run it).
 */
public final class PerformanceHarness {
    private static final int[] SIZES = {1000, 2000, 4000, 8000, 16000, 32000, 64000};

    public static void main(String[] args) {
        double matchRate = args.length > 0 ? Double.parseDouble(args[0]) : 1.0;

        Expr tree = new Parser("R join[R.b=S.b] S").parseQuery();

        System.out.println("| n | m | comparisons | wall time (s) | output tuples |");
        System.out.println("|---|---|---|---|---|");

        for (int size : SIZES) {
            Map<String, Relation> relations = DataGenerator.generateRelations(size, size, matchRate);
            Evaluator evaluator = new Evaluator(relations);

            long start = System.nanoTime();
            Relation result = evaluator.evaluate(tree);
            long elapsedNanos = System.nanoTime() - start;

            System.out.printf("| %d | %d | %d | %.4f | %d |%n",
                    size, size, evaluator.joinComparisons, elapsedNanos / 1_000_000_000.0, result.tuples.size());
        }
    }
}
