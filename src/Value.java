import java.math.BigDecimal;

/**
 * A single value inside a tuple: either a number or a string. Comparing a
 * NumValue to a StrValue is always a type error (Section 4.3: "a
 * comparison between a number and a string is also an error rather than
 * a silent false") -- that check lives in Evaluator.compare(), not here.
 *
 * Numbers use BigDecimal rather than double specifically to avoid binary
 * floating-point surprises (e.g. 0.1 + 0.2 != 0.3) affecting equality
 * checks used for tuple deduplication and set operations.
 */
public abstract class Value {
    public abstract String typeName(); // "number" or "string" -- used in type-error messages

    public static final class NumValue extends Value {
        public final BigDecimal value;

        public NumValue(BigDecimal value) {
            this.value = value;
        }

        public String typeName() {
            return "number";
        }

        @Override
        public boolean equals(Object o) {
            // compareTo, not equals -- BigDecimal's own equals() treats 30 and 30.0 as
            // different, which would be a strange way for two numbers to be "unequal".
            return o instanceof NumValue other && value.compareTo(other.value) == 0;
        }

        @Override
        public int hashCode() {
            return value.stripTrailingZeros().toPlainString().hashCode();
        }

        @Override
        public String toString() {
            return value.stripTrailingZeros().toPlainString();
        }
    }

    public static final class StrValue extends Value {
        public final String value;

        public StrValue(String value) {
            this.value = value;
        }

        public String typeName() {
            return "string";
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof StrValue other && value.equals(other.value);
        }

        @Override
        public int hashCode() {
            return value.hashCode();
        }

        @Override
        public String toString() {
            return value;
        }
    }
}
