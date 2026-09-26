import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Hand-written scanner: no regular expressions, no parser-generator lexer.
 * Call next() repeatedly (or tokenizeAll() for convenience) to pull tokens
 * out of a source string one at a time.
 *
 * Design notes worth remembering for the grammar document / oral check:
 *
 *  - Maximal munch for comparison operators: on seeing '>' (or '<', '!')
 *    we ALWAYS check the following character before deciding whether the
 *    token is '>' or '>=' (etc). See scanOperatorOrPunct().
 *
 *  - '-' is only ever the start of a NUMBER. The language has no binary
 *    subtraction symbol (relational "minus" is spelled as a keyword), so
 *    there's no ambiguity: '>' followed by '-30' always tokenizes as
 *    GT, NUMBER(-30) -- never some invented ">-" operator.
 *
 *  - Strings are scanned with their own mini state machine that does NOT
 *    care about parentheses or commas -- '(' and ',' inside a quoted
 *    string are just characters being appended to the string's contents.
 *    A doubled quote '' inside a string is one literal quote character;
 *    a single quote not followed by another quote ends the string.
 */
public final class Tokenizer {

    private static final Map<String, TokenType> KEYWORDS = new HashMap<>();
    static {
        KEYWORDS.put("select", TokenType.SELECT);
        KEYWORDS.put("project", TokenType.PROJECT);
        KEYWORDS.put("rename", TokenType.RENAME);
        KEYWORDS.put("union", TokenType.UNION);
        KEYWORDS.put("intersect", TokenType.INTERSECT);
        KEYWORDS.put("minus", TokenType.MINUS);
        KEYWORDS.put("times", TokenType.TIMES);
        KEYWORDS.put("join", TokenType.JOIN);
        KEYWORDS.put("and", TokenType.AND);
        KEYWORDS.put("or", TokenType.OR);
        KEYWORDS.put("not", TokenType.NOT);
    }

    private final String source;
    private final char[] chars;
    private final int len;
    private int pos;

    public Tokenizer(String source) {
        this.source = source;
        this.chars = source.toCharArray();
        this.len = chars.length;
        this.pos = 0;
    }

    /** Returns the next token. Returns an EOF token forever once input is exhausted. */
    public Token next() {
        skipWhitespace();

        if (pos >= len) {
            return new Token(TokenType.EOF, "", len);
        }

        char c = chars[pos];

        if (isIdentStart(c)) {
            return scanIdentifierOrKeyword();
        }
        if (Character.isDigit(c)) {
            return scanNumber();
        }
        if (c == '-' && pos + 1 < len && Character.isDigit(chars[pos + 1])) {
            return scanNumber();
        }
        if (c == '\'') {
            return scanString();
        }
        return scanOperatorOrPunct();
    }

    /** Convenience for tests/tools: scan the whole input into a list, ending with EOF. */
    public List<Token> tokenizeAll() {
        List<Token> tokens = new ArrayList<>();
        Token t;
        do {
            t = next();
            tokens.add(t);
        } while (t.type != TokenType.EOF);
        return tokens;
    }

    // ---- character classes ----

    private static boolean isIdentStart(char c) {
        return Character.isLetter(c) || c == '_';
    }

    private static boolean isIdentPart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private void skipWhitespace() {
        while (pos < len && Character.isWhitespace(chars[pos])) {
            pos++;
        }
    }

    // ---- scanners ----

    private Token scanIdentifierOrKeyword() {
        int start = pos;
        while (pos < len && isIdentPart(chars[pos])) {
            pos++;
        }
        String word = source.substring(start, pos);
        TokenType type = KEYWORDS.getOrDefault(word, TokenType.IDENT);
        return new Token(type, word, start);
    }

    private Token scanNumber() {
        int start = pos;
        if (chars[pos] == '-') {
            pos++;
        }
        while (pos < len && Character.isDigit(chars[pos])) {
            pos++;
        }
        // optional decimal part; only consume '.' if it's actually followed by a digit,
        // so "3.foo" doesn't swallow the '.' into the number.
        if (pos < len && chars[pos] == '.' && pos + 1 < len && Character.isDigit(chars[pos + 1])) {
            pos++;
            while (pos < len && Character.isDigit(chars[pos])) {
                pos++;
            }
        }
        String text = source.substring(start, pos);
        return new Token(TokenType.NUMBER, text, start);
    }

    private Token scanString() {
        int start = pos;
        pos++; // consume opening quote
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (pos >= len) {
                throw new LexicalException("unterminated string literal", start);
            }
            char c = chars[pos];
            if (c == '\'') {
                if (pos + 1 < len && chars[pos + 1] == '\'') {
                    // doubled quote => one literal quote character
                    sb.append('\'');
                    pos += 2;
                    continue;
                } else {
                    pos++; // consume closing quote
                    break;
                }
            } else {
                sb.append(c);
                pos++;
            }
        }
        return new Token(TokenType.STRING, sb.toString(), start);
    }

    private Token scanOperatorOrPunct() {
        int start = pos;
        char c = chars[pos];
        switch (c) {
            case '(':
                pos++;
                return new Token(TokenType.LPAREN, "(", start);
            case ')':
                pos++;
                return new Token(TokenType.RPAREN, ")", start);
            case '[':
                pos++;
                return new Token(TokenType.LBRACKET, "[", start);
            case ']':
                pos++;
                return new Token(TokenType.RBRACKET, "]", start);
            case ',':
                pos++;
                return new Token(TokenType.COMMA, ",", start);
            case '.':
                pos++;
                return new Token(TokenType.DOT, ".", start);
            case '=':
                pos++;
                return new Token(TokenType.EQ, "=", start);
            case '!':
                pos++;
                if (pos < len && chars[pos] == '=') {
                    pos++;
                    return new Token(TokenType.NEQ, "!=", start);
                }
                throw new LexicalException("expected '=' after '!'", start);
            case '<':
                pos++;
                if (pos < len && chars[pos] == '=') {
                    pos++;
                    return new Token(TokenType.LE, "<=", start);
                }
                return new Token(TokenType.LT, "<", start);
            case '>':
                pos++;
                if (pos < len && chars[pos] == '=') {
                    pos++;
                    return new Token(TokenType.GE, ">=", start);
                }
                return new Token(TokenType.GT, ">", start);
            default:
                throw new LexicalException("unexpected character '" + c + "'", start);
        }
    }
}
