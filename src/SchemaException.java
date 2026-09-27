/** Union/intersect/minus schema mismatch, a times/join column collision, or a malformed relation definition. */
public final class SchemaException extends EvalException {
    public SchemaException(String message) {
        super(message);
    }
}
