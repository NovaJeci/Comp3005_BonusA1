/** A comparison mixed a number and a string (Section 4.3: this is an error, not a silent false). */
public final class TypeException extends EvalException {
    public TypeException(String message) {
        super(message);
    }
}
