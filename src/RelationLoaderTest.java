import java.util.Map;

/**
 * Covers relation loading (Section 4.1): comment/blank-line handling,
 * set semantics (duplicate tuples collapse), and quoted values.
 * Run with:
 *   javac -d out source/*.java tests/*.java
 *   java -cp out RelationLoaderTest
 */
public class RelationLoaderTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {

        // basic load: 3 tuples, 4 attributes, matching the Section 4.1 example exactly
        Map<String, Relation> r1 = RelationLoader.load(
                "// employees and their departments\n" +
                "Employees (EID, Name, Age, DID) = {\n" +
                "  E1, John, 32, D1\n" +
                "  E2, Alice, 28, D2\n" +
                "  E3, Bob, 29, D1\n" +
                "}\n");
        check("basic load has one relation", r1.size() == 1);
        check("Employees has 4 attributes", r1.get("Employees").schema.size() == 4);
        check("Employees has 3 tuples", r1.get("Employees").tuples.size() == 3);

        // duplicate tuple lines collapse -- "a relation is a set"
        Map<String, Relation> r2 = RelationLoader.load(
                "R (A, B) = {\n" +
                "  1, 2\n" +
                "  1, 2\n" +
                "  3, 4\n" +
                "}\n");
        check("duplicate tuples collapse to 2", r2.get("R").tuples.size() == 2);

        // blank lines and comment lines inside the body are ignored, not counted as tuples
        Map<String, Relation> r3 = RelationLoader.load(
                "R (A) = {\n" +
                "\n" +
                "  // a comment inside the body\n" +
                "  1\n" +
                "\n" +
                "  2\n" +
                "}\n");
        check("blank/comment lines don't become tuples", r3.get("R").tuples.size() == 2);

        // quoted value containing a comma and a space is not split
        Map<String, Relation> r4 = RelationLoader.load(
                "R (Name) = {\n" +
                "  'Smith, Jane'\n" +
                "}\n");
        Tuple t = r4.get("R").tuples.iterator().next();
        check("quoted comma-containing value stays one field",
                t.values.size() == 1 && t.values.get(0) instanceof Value.StrValue sv && sv.value.equals("Smith, Jane"));

        // multiple relations in one source
        Map<String, Relation> r5 = RelationLoader.load(
                "R (A) = {\n  1\n}\n" +
                "S (B) = {\n  2\n}\n");
        check("two relations both load", r5.size() == 2 && r5.containsKey("R") && r5.containsKey("S"));

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    private static void check(String label, boolean ok) {
        if (ok) {
            passed++;
            System.out.println("PASS  " + label);
        } else {
            failed++;
            System.out.println("FAIL  " + label);
        }
    }
}
