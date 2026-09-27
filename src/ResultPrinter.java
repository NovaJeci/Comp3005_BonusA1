import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Prints a Relation as a simple table: header row, then one row per tuple.
 * For an empty relation this naturally prints just the header and nothing
 * else -- "prints the schema and an empty body, cleanly" (case 25).
 */
public final class ResultPrinter {
    private ResultPrinter() {}

    public static String print(Relation r) {
        StringBuilder sb = new StringBuilder();
        List<String> labels = columnLabels(r.schema);
        sb.append(String.join(" | ", labels)).append('\n');
        for (Tuple t : r.tuples) {
            List<String> cells = new ArrayList<>();
            for (Value v : t.values) cells.add(v.toString());
            sb.append(String.join(" | ", cells)).append('\n');
        }
        return sb.toString();
    }

    /** Uses the bare attribute name unless it's not unique in this schema, then qualifies it. */
    private static List<String> columnLabels(Schema schema) {
        Map<String, Integer> counts = new HashMap<>();
        for (Attribute a : schema.columns) counts.merge(a.name, 1, Integer::sum);
        List<String> labels = new ArrayList<>();
        for (Attribute a : schema.columns) {
            labels.add(counts.get(a.name) > 1 ? a.qualifiedName() : a.name);
        }
        return labels;
    }
}
