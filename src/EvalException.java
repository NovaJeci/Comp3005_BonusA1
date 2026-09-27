/**
 * Base class for semantic errors raised during evaluation (as opposed to
 * LexicalException/SyntaxException, which come from tokenizing/parsing).
 * Section 6.3 only requires a position for parse errors, not for these,
 * so these just carry a clear message.
 */
public class EvalException extends RuntimeException {
    public EvalException(String message) {
        super(message);
    }
}
