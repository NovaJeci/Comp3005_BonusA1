/**
 * One side of a comparison inside a condition: an attribute reference,
 * a number literal, or a string literal.
 */
public abstract class Operand {

    /** Compact inline rendering used inside a Condition's toInline(), e.g. "Attr(Age)". */
    public abstract String toInline();

    public static final class AttrRef extends Operand {
        /** Relation qualifier, e.g. "Emp" in "Emp.DID" -- null when unqualified. */
        public final String qualifier;
        public final String name;

        public AttrRef(String qualifier, String name) {
            this.qualifier = qualifier;
            this.name = name;
        }

        public String toInline() {
            return "Attr(" + (qualifier != null ? qualifier + "." + name : name) + ")";
        }
    }

    public static final class NumberLit extends Operand {
        /** Raw numeric text as scanned, e.g. "30", "-30", "3.5". */
        public final String text;

        public NumberLit(String text) {
            this.text = text;
        }

        public String toInline() {
            return "Num(" + text + ")";
        }
    }

    public static final class StringLit extends Operand {
        /** Decoded value -- quotes already stripped and '' collapsed by the tokenizer. */
        public final String value;

        public StringLit(String value) {
            this.value = value;
        }

        public String toInline() {
            return "Str('" + value + "')";
        }
    }
}
