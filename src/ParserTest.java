/**
 * Covers Section 7.2 (grammar and precedence), cases 10-17.
 * Run with:
 *   javac -d out source/*.java tests/*.java
 *   java -cp out ParserTest
 */
public class ParserTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {

        // case 10: A union B minus C groups as (A union B) minus C (documented in GRAMMAR.md)
        expectTree("A union B minus C",
                "Minus\n" +
                "├── Union\n" +
                "│   ├── Relation(A)\n" +
                "│   └── Relation(B)\n" +
                "└── Relation(C)\n");

        // case 11: A minus B minus C is left-associative: (A minus B) minus C
        expectTree("A minus B minus C",
                "Minus\n" +
                "├── Minus\n" +
                "│   ├── Relation(A)\n" +
                "│   └── Relation(B)\n" +
                "└── Relation(C)\n");

        // case 12: not binds tighter than and, which binds tighter than or
        expectTree("select[not (a=1 and b=2) or c>3](R)",
                "Select(cond=Or(Not(And(Eq(Attr(a), Num(1)), Eq(Attr(b), Num(2)))), Gt(Attr(c), Num(3))))\n" +
                "└── Relation(R)\n");

        // case 13: a=1 and b=2 or c=3 groups as (a=1 and b=2) or c=3
        expectTree("select[a=1 and b=2 or c=3](R)",
                "Select(cond=Or(And(Eq(Attr(a), Num(1)), Eq(Attr(b), Num(2))), Eq(Attr(c), Num(3))))\n" +
                "└── Relation(R)\n");

        // case 14: three levels of nesting evaluate in the right order
        expectTree("project[Name](select[Age>30](select[DID='D1'](Employees)))",
                "Project(attrs=[Attr(Name)])\n" +
                "└── Select(cond=Gt(Attr(Age), Num(30)))\n" +
                "    └── Select(cond=Eq(Attr(DID), Str('D1')))\n" +
                "        └── Relation(Employees)\n");

        // case 15: explicit parentheses override precedence
        expectTree("(A union B) minus (C intersect D)",
                "Minus\n" +
                "├── Union\n" +
                "│   ├── Relation(A)\n" +
                "│   └── Relation(B)\n" +
                "└── Intersect\n" +
                "    ├── Relation(C)\n" +
                "    └── Relation(D)\n");

        // case 16: missing closing parenthesis -> syntax error with a position
        expectSyntaxError("select[Age>30](R");

        // case 17: empty attribute list is not a valid projection
        expectSyntaxError("project[](R)");

        // bonus: matches the exact example from Section 6.2 of the assignment
        expectTree("project[Name](select[Age>30](Employees))",
                "Project(attrs=[Attr(Name)])\n" +
                "└── Select(cond=Gt(Attr(Age), Num(30)))\n" +
                "    └── Relation(Employees)\n");

        // bonus: keyword-as-attribute-name (case 8) also has to work at the parser level,
        // not just survive tokenizing
        expectTree("select[union=3](R)",
                "Select(cond=Eq(Attr(union), Num(3)))\n" +
                "└── Relation(R)\n");

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static void expectTree(String query, String expectedTree) {
        String actual;
        try {
            Expr tree = new Parser(query).parseQuery();
            actual = TreePrinter.print(tree);
        } catch (RuntimeException e) {
            actual = "EXCEPTION: " + e.getMessage();
        }
        report(actual.equals(expectedTree), "parse(\"" + query + "\")", expectedTree, actual);
    }

    private static void expectSyntaxError(String query) {
        try {
            new Parser(query).parseQuery();
            report(false, "parse(\"" + query + "\")", "SyntaxException", "no exception thrown");
        } catch (SyntaxException e) {
            report(true, "parse(\"" + query + "\")", "SyntaxException", e.getMessage());
        }
    }

    private static void report(boolean ok, String label, String expected, String actual) {
        if (ok) {
            passed++;
            System.out.println("PASS  " + label);
        } else {
            failed++;
            System.out.println("FAIL  " + label);
            System.out.println("      expected:\n" + indent(expected));
            System.out.println("      actual:\n" + indent(actual));
        }
    }

    private static String indent(String s) {
        return "        " + s.replace("\n", "\n        ");
    }
}
