/**
 * Thrown by the Parser for syntax errors (unbalanced parens, missing
 * operand, empty attribute list, etc.). Like LexicalException, this must
 * be caught at the top level and printed as a clean message -- never
 * allowed to surface as a raw Java stack trace (Section 6.3).
 */
public class SyntaxException extends RuntimeException {
    public final int position;

    public SyntaxException(String message, int position) {
        super(message + " (at position " + position + ")");
        this.position = position;
    }
}
