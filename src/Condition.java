/**
 * A boolean condition, as used inside select[...] and join[...].
 * These classes just hold structure; the precedence (or < and < not <
 * comparison) is enforced by WHICH parser method builds which node --
 * see Parser.parseOrCond/parseAndCond/parseNotCond/parseComparison.
 */
public abstract class Condition {

    /** Compact inline rendering for the --tree printer, e.g. "Gt(Attr(Age), Num(30))". */
    public abstract String toInline();

    public static final class Or extends Condition {
        public final Condition left;
        public final Condition right;

        public Or(Condition left, Condition right) {
            this.left = left;
            this.right = right;
        }

        public String toInline() {
            return "Or(" + left.toInline() + ", " + right.toInline() + ")";
        }
    }

    public static final class And extends Condition {
        public final Condition left;
        public final Condition right;

        public And(Condition left, Condition right) {
            this.left = left;
            this.right = right;
        }

        public String toInline() {
            return "And(" + left.toInline() + ", " + right.toInline() + ")";
        }
    }

    public static final class Not extends Condition {
        public final Condition inner;

        public Not(Condition inner) {
            this.inner = inner;
        }

        public String toInline() {
            return "Not(" + inner.toInline() + ")";
        }
    }

    public static final class Comparison extends Condition {
        public final Operand left;
        public final TokenType op; // one of EQ, NEQ, LT, LE, GT, GE
        public final Operand right;

        public Comparison(Operand left, TokenType op, Operand right) {
            this.left = left;
            this.op = op;
            this.right = right;
        }

        public String toInline() {
            return opName(op) + "(" + left.toInline() + ", " + right.toInline() + ")";
        }

        private static String opName(TokenType op) {
            switch (op) {
                case EQ: return "Eq";
                case NEQ: return "Neq";
                case LT: return "Lt";
                case LE: return "Le";
                case GT: return "Gt";
                case GE: return "Ge";
                default: throw new IllegalStateException("not a comparison operator: " + op);
            }
        }
    }
}
