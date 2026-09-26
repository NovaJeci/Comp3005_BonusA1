/**
 * Thrown by the Tokenizer for lexical errors (e.g. an unterminated string).
 * The top-level program must catch this — and its sibling parser/semantic
 * exceptions — and print a clean message instead of letting it propagate
 * into a Java stack trace (Section 6.3 of the assignment).
 */
public class LexicalException extends RuntimeException {
    public final int position;

    public LexicalException(String message, int position) {
        super(message + " (at position " + position + ")");
        this.position = position;
    }
}
