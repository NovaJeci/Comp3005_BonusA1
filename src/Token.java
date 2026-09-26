/**
 * One scanned token.
 *
 * `text` is the *decoded* value where that matters: for a STRING token it is
 * the string contents with the surrounding quotes stripped and doubled
 * quotes ('') collapsed to one literal quote — not the raw source slice.
 * For everything else it's the literal characters consumed (e.g. "-30",
 * "union", ">=").
 *
 * `position` is the character offset (0-based) of the FIRST character of
 * this token in the original source string. Every error message in this
 * project is built around this number, so it must always point at the
 * start of the token, not the end.
 */
public final class Token {
    public final TokenType type;
    public final String text;
    public final int position;

    public Token(TokenType type, String text, int position) {
        this.type = type;
        this.text = text;
        this.position = position;
    }

    @Override
    public String toString() {
        return type + "(" + text + ")@" + position;
    }
}
