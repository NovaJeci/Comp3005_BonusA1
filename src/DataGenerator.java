import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Section 8.1: generates R(a, b) and S(b, c) at a chosen size, with a
 * controllable match rate (roughly how many S tuples each R tuple joins
 * with).
 *
 * Usage: java DataGenerator <n> <m> <matchRate> <outputPrefix>
 *   writes <outputPrefix>-R.txt and <outputPrefix>-S.txt in the same
 *   relation-definition format RelationLoader reads.
 *
 * DESIGN: the join attribute "b" is assigned round-robin over a domain of
 * size D = round(m / matchRate) in BOTH relations. Since S's m tuples
 * spread evenly over D domain values, each domain value appears in about
 * matchRate S-tuples -- so any R-tuple lands on a domain value that
 * matches about matchRate S-tuples, by construction rather than luck.
 * This also makes the total comparison count and output size predictable
 * enough to sanity-check against the formula you derive in REPORT.md
 * (Section 8.4, question 1).
 */
public final class DataGenerator {

    public static void main(String[] args) {
        if (args.length != 4) {
            System.out.println("Usage: java DataGenerator <n> <m> <matchRate> <outputPrefix>");
            return;
        }
        int n = Integer.parseInt(args[0]);
        int m = Integer.parseInt(args[1]);
        double matchRate = Double.parseDouble(args[2]);
        String prefix = args[3];

        Map<String, Relation> relations = generateRelations(n, m, matchRate);
        int domainSize = Math.max(1, (int) Math.round(m / matchRate));

        try {
            Files.writeString(Path.of(prefix + "-R.txt"), toDefText("R", relations.get("R")), StandardCharsets.UTF_8);
            Files.writeString(Path.of(prefix + "-S.txt"), toDefText("S", relations.get("S")), StandardCharsets.UTF_8);
            System.out.println("Wrote " + prefix + "-R.txt (" + n + " tuples) and "
                    + prefix + "-S.txt (" + m + " tuples); join-attribute domain size = " + domainSize);
        } catch (IOException e) {
            System.out.println("Error writing files: " + e.getMessage());
        }
    }

    /**
     * Builds R and S directly as in-memory Relation objects, bypassing file
     * I/O and the text-based RelationLoader entirely. PerformanceHarness
     * uses this so that a timed run measures only the join itself.
     */
    public static Map<String, Relation> generateRelations(int n, int m, double matchRate) {
        int domainSize = Math.max(1, (int) Math.round(m / matchRate));

        List<Attribute> rCols = List.of(new Attribute("R", "a"), new Attribute("R", "b"));
        Set<Tuple> rTuples = new LinkedHashSet<>();
        for (int i = 0; i < n; i++) {
            rTuples.add(new Tuple(List.of(
                    new Value.NumValue(BigDecimal.valueOf(i)),
                    new Value.NumValue(BigDecimal.valueOf(i % domainSize)))));
        }

        List<Attribute> sCols = List.of(new Attribute("S", "b"), new Attribute("S", "c"));
        Set<Tuple> sTuples = new LinkedHashSet<>();
        for (int i = 0; i < m; i++) {
            sTuples.add(new Tuple(List.of(
                    new Value.NumValue(BigDecimal.valueOf(i % domainSize)),
                    new Value.NumValue(BigDecimal.valueOf(i)))));
        }

        Map<String, Relation> result = new LinkedHashMap<>();
        result.put("R", new Relation(new Schema(rCols), rTuples));
        result.put("S", new Relation(new Schema(sCols), sTuples));
        return result;
    }

    private static String toDefText(String name, Relation r) {
        StringBuilder sb = new StringBuilder();
        sb.append(name).append(" (");
        for (int i = 0; i < r.schema.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(r.schema.get(i).name);
        }
        sb.append(") = {\n");
        for (Tuple t : r.tuples) {
            List<String> cells = new ArrayList<>();
            for (Value v : t.values) cells.add(v.toString());
            sb.append("  ").append(String.join(", ", cells)).append("\n");
        }
        sb.append("}\n");
        return sb.toString();
    }
}
