/** A query refers to a relation or attribute that doesn't exist, or an unqualified name that's ambiguous. */
public final class NameException extends EvalException {
    public NameException(String message) {
        super(message);
    }
}
