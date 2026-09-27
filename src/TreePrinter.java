import java.util.List;

/**
 * Prints a parse tree in the style shown in Section 6.2 of the assignment:
 *
 *   Project(attrs=[Name])
 *   └── Select(cond=Gt(Attr(Age), Num(30)))
 *       └── Relation(Employees)
 *
 * Works on any Expr node uniformly (0, 1, or 2 children) since every Expr
 * exposes label() and children().
 */
public final class TreePrinter {
    private TreePrinter() {}

    public static String print(Expr root) {
        StringBuilder sb = new StringBuilder();
        sb.append(root.label()).append('\n');
        List<Expr> kids = root.children();
        for (int i = 0; i < kids.size(); i++) {
            printChild(kids.get(i), "", i == kids.size() - 1, sb);
        }
        return sb.toString();
    }

    private static void printChild(Expr node, String prefix, boolean isLast, StringBuilder sb) {
        sb.append(prefix).append(isLast ? "└── " : "├── ").append(node.label()).append('\n');
        String childPrefix = prefix + (isLast ? "    " : "│   ");
        List<Expr> kids = node.children();
        for (int i = 0; i < kids.size(); i++) {
            printChild(kids.get(i), childPrefix, i == kids.size() - 1, sb);
        }
    }
}
