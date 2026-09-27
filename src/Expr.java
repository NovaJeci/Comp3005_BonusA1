import java.util.List;

/**
 * A node in a relational-algebra parse tree. Every node knows its own
 * one-line label (for the --tree printer, Section 6.2) and its children.
 * Keeping label()/children() on every node means TreePrinter can walk ANY
 * node the same way, whether it has 0, 1, or 2 children.
 */
public abstract class Expr {

    public abstract String label();

    public abstract List<Expr> children();

    /** A bare relation reference, e.g. "Employees" or "R". */
    public static final class RelationRef extends Expr {
        public final String name;

        public RelationRef(String name) {
            this.name = name;
        }

        public String label() {
            return "Relation(" + name + ")";
        }

        public List<Expr> children() {
            return List.of();
        }
    }

    public static final class SelectOp extends Expr {
        public final Condition condition;
        public final Expr input;

        public SelectOp(Condition condition, Expr input) {
            this.condition = condition;
            this.input = input;
        }

        public String label() {
            return "Select(cond=" + condition.toInline() + ")";
        }

        public List<Expr> children() {
            return List.of(input);
        }
    }

    public static final class ProjectOp extends Expr {
        public final List<Operand.AttrRef> attrs;
        public final Expr input;

        public ProjectOp(List<Operand.AttrRef> attrs, Expr input) {
            this.attrs = attrs;
            this.input = input;
        }

        public String label() {
            StringBuilder sb = new StringBuilder("Project(attrs=[");
            for (int i = 0; i < attrs.size(); i++) {
                if (i > 0) sb.append(", ");
                sb.append(attrs.get(i).toInline());
            }
            sb.append("])");
            return sb.toString();
        }

        public List<Expr> children() {
            return List.of(input);
        }
    }

    public static final class RenameOp extends Expr {
        public final String newName;
        public final Expr input;

        public RenameOp(String newName, Expr input) {
            this.newName = newName;
            this.input = input;
        }

        public String label() {
            return "Rename(" + newName + ")";
        }

        public List<Expr> children() {
            return List.of(input);
        }
    }

    /** union / intersect / minus / times -- all same shape: two children, no parameter. */
    public enum BinaryKind { UNION, INTERSECT, MINUS, TIMES }

    public static final class BinaryOp extends Expr {
        public final BinaryKind kind;
        public final Expr left;
        public final Expr right;

        public BinaryOp(BinaryKind kind, Expr left, Expr right) {
            this.kind = kind;
            this.left = left;
            this.right = right;
        }

        public String label() {
            switch (kind) {
                case UNION: return "Union";
                case INTERSECT: return "Intersect";
                case MINUS: return "Minus";
                case TIMES: return "Times";
                default: throw new IllegalStateException("unreachable");
            }
        }

        public List<Expr> children() {
            return List.of(left, right);
        }
    }

    /** join[cond] -- like BinaryOp, but carries a condition parameter (it's times + select fused). */
    public static final class JoinOp extends Expr {
        public final Condition condition;
        public final Expr left;
        public final Expr right;

        public JoinOp(Condition condition, Expr left, Expr right) {
            this.condition = condition;
            this.left = left;
            this.right = right;
        }

        public String label() {
            return "Join(cond=" + condition.toInline() + ")";
        }

        public List<Expr> children() {
            return List.of(left, right);
        }
    }
}
