import java.util.Map;
public class MatchRateTest {
    public static void main(String[] args) {
        double[] rates = {0.2, 1.0, 5.0, 20.0};
        int size = 4000;
        Expr tree = new Parser("R join[R.b=S.b] S").parseQuery();
        System.out.println("| match rate | comparisons | wall time (s) | output tuples |");
        System.out.println("|---|---|---|---|");
        for (double rate : rates) {
            Map<String, Relation> relations = DataGenerator.generateRelations(size, size, rate);
            Evaluator evaluator = new Evaluator(relations);
            long start = System.nanoTime();
            Relation result = evaluator.evaluate(tree);
            long elapsed = System.nanoTime() - start;
            System.out.printf("| %.1f | %d | %.4f | %d |%n", rate, evaluator.joinComparisons, elapsed/1e9, result.tuples.size());
        }
    }
}
