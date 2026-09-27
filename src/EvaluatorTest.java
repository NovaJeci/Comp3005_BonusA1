import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Covers Section 7.3 (semantics), cases 18-25.
 * Run with:
 *   javac -d out source/*.java tests/*.java
 *   java -cp out EvaluatorTest
 */
public class EvaluatorTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {

        // case 18: select[A=B](R) compares two ATTRIBUTES against each other, not a column
        // against the literal string "B"
        Map<String, Relation> rel18 = RelationLoader.load(
                "R (A, B) = {\n  1, 1\n  2, 3\n  5, 5\n}\n");
        expectTuples(rel18, "select[A=B](R)", Set.of("[1, 1]", "[5, 5]"));

        // case 19: qualified names resolve; the output schema keeps both DID columns distinguishable
        Map<String, Relation> rel19 = RelationLoader.load(
                "Emp (EID, Name, DID) = {\n  E1, John, D1\n  E2, Alice, D2\n}\n" +
                "Dept (DID, DName) = {\n  D1, Sales\n  D2, Engineering\n}\n");
        expectTuples(rel19, "Emp join[Emp.DID=Dept.DID] Dept",
                Set.of("[E1, John, D1, D1, Sales]", "[E2, Alice, D2, D2, Engineering]"));

        // case 20: self join -- only answerable because rename gives one side a different qualifier
        Map<String, Relation> rel20 = RelationLoader.load(
                "Emp (EID, Name, MgrID) = {\n  E1, Alice, E2\n  E2, Bob, E2\n}\n");
        expectTuples(rel20, "rename[E2](Emp) join[Emp.MgrID=E2.EID] Emp",
                Set.of("[E2, Bob, E2, E1, Alice, E2]", "[E2, Bob, E2, E2, Bob, E2]"));
        // and the *reason* it's needed: without rename, both sides collide
        expectSchemaError(rel20, "Emp join[Emp.MgrID=Emp.EID] Emp");

        // case 21: union of relations with different schemas is a schema error, not a crash
        Map<String, Relation> rel21 = RelationLoader.load(
                "R (A, B) = {\n  1, 2\n}\n" +
                "S (X, Y, Z) = {\n  1, 2, 3\n}\n");
        expectSchemaError(rel21, "R union S");

        // case 22: comparing a number attribute to a string literal is a type error
        Map<String, Relation> rel22 = RelationLoader.load("R (Age) = {\n  30\n}\n");
        expectTypeError(rel22, "select[Age>'30'](R)");

        // case 23: projection removes duplicates -- 3 employees, 2 distinct departments
        Map<String, Relation> rel23 = RelationLoader.load(
                "Employees (EID, Name, Age, DID) = {\n" +
                "  E1, John, 32, D1\n" +
                "  E2, Alice, 28, D2\n" +
                "  E3, Bob, 29, D1\n" +
                "}\n");
        expectTuples(rel23, "project[DID](Employees)", Set.of("[D1]", "[D2]"));

        // case 24: project[Name, Name](R) -- documented decision: this is a schema error
        Map<String, Relation> rel24 = RelationLoader.load("R (Name) = {\n  Bob\n}\n");
        expectSchemaError(rel24, "project[Name, Name](R)");

        // case 25: a query returning no tuples prints its schema with a clean empty body
        Map<String, Relation> rel25 = RelationLoader.load("R (A) = {\n}\n");
        Relation empty = new Evaluator(rel25).evaluate(new Parser("select[A=999](R)").parseQuery());
        check("case 25: empty relation has 0 tuples", empty.tuples.isEmpty());
        check("case 25: empty relation keeps its schema", empty.schema.size() == 1);
        String printed = ResultPrinter.print(empty);
        check("case 25: prints header with no rows", printed.equals("A\n"));

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    private static void expectTuples(Map<String, Relation> relations, String query, Set<String> expected) {
        try {
            Relation result = new Evaluator(relations).evaluate(new Parser(query).parseQuery());
            Set<String> actual = tupleStrings(result);
            report(actual.equals(expected), "evaluate(\"" + query + "\")", expected.toString(), actual.toString());
        } catch (RuntimeException e) {
            report(false, "evaluate(\"" + query + "\")", expected.toString(), "EXCEPTION: " + e.getMessage());
        }
    }

    private static void expectSchemaError(Map<String, Relation> relations, String query) {
        try {
            new Evaluator(relations).evaluate(new Parser(query).parseQuery());
            report(false, "evaluate(\"" + query + "\")", "SchemaException", "no exception thrown");
        } catch (SchemaException e) {
            report(true, "evaluate(\"" + query + "\")", "SchemaException", e.getMessage());
        }
    }

    private static void expectTypeError(Map<String, Relation> relations, String query) {
        try {
            new Evaluator(relations).evaluate(new Parser(query).parseQuery());
            report(false, "evaluate(\"" + query + "\")", "TypeException", "no exception thrown");
        } catch (TypeException e) {
            report(true, "evaluate(\"" + query + "\")", "TypeException", e.getMessage());
        }
    }

    private static Set<String> tupleStrings(Relation r) {
        Set<String> out = new TreeSet<>();
        for (Tuple t : r.tuples) {
            List<String> cells = new ArrayList<>();
            for (Value v : t.values) cells.add(v.toString());
            out.add("[" + String.join(", ", cells) + "]");
        }
        return out;
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

    private static void report(boolean ok, String label, String expected, String actual) {
        if (ok) {
            passed++;
            System.out.println("PASS  " + label);
        } else {
            failed++;
            System.out.println("FAIL  " + label);
            System.out.println("      expected: " + expected);
            System.out.println("      actual:   " + actual);
        }
    }
}
