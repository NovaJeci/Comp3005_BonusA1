import java.util.Map;

/**
 * Measures select[b < n/2](R) and project[b](R) at the same sizes as
 * PerformanceHarness's join experiment, for Section 8.4 Question 3.
 * Uses DataGenerator.generateRelations() the same way, so R is built
 * identically (b values 0..n-1, all distinct, since matchRate=1.0 gives a
 * domain size equal to n).
 */
public final class SelectProjectHarness {
    private static final int[] SIZES = {1000, 2000, 4000, 8000, 16000, 32000, 64000};

    public static void main(String[] args) {
        System.out.println("### select[b < n/2](R)");
        System.out.println("| n | comparisons | wall time (s) | output tuples |");
        System.out.println("|---|---|---|---|");
        for (int size : SIZES) {
            Map<String, Relation> relations = DataGenerator.generateRelations(size, size, 1.0);
            Expr tree = new Parser("select[b<" + (size / 2) + "](R)").parseQuery();
            Evaluator evaluator = new Evaluator(relations);

            long start = System.nanoTime();
            Relation result = evaluator.evaluate(tree);
            long elapsed = System.nanoTime() - start;

            System.out.printf("| %d | %d | %.4f | %d |%n",
                    size, evaluator.selectComparisons, elapsed / 1_000_000_000.0, result.tuples.size());
        }

        System.out.println();
        System.out.println("### project[b](R)");
        System.out.println("| n | wall time (s) | output tuples |");
        System.out.println("|---|---|---|");
        for (int size : SIZES) {
            Map<String, Relation> relations = DataGenerator.generateRelations(size, size, 1.0);
            Expr tree = new Parser("project[b](R)").parseQuery();
            Evaluator evaluator = new Evaluator(relations);

            long start = System.nanoTime();
            Relation result = evaluator.evaluate(tree);
            long elapsed = System.nanoTime() - start;

            System.out.printf("| %d | %.4f | %d |%n", size, elapsed / 1_000_000_000.0, result.tuples.size());
        }
    }
}
