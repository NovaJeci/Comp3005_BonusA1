/**
 * Every kind of token the hand-written scanner can produce.
 *
 * Keywords (UNION, SELECT, ...) are still tokenized as themselves even when
 * they appear where an attribute name would make sense (see case 8 in the
 * assignment: select[union=3](R)). It's the PARSER's job, not the
 * tokenizer's, to accept a keyword token as an attribute name in that
 * position. Keeping that decision in the parser (rather than making the
 * lexer context-sensitive) keeps the lexer simple and context-free.
 */
public enum TokenType {
    // literals / names
    IDENT, NUMBER, STRING,

    // keywords
    UNION, INTERSECT, MINUS, TIMES, JOIN, SELECT, PROJECT, RENAME,
    AND, OR, NOT,

    // punctuation
    LPAREN, RPAREN, LBRACKET, RBRACKET, COMMA, DOT,

    // comparison operators
    EQ, NEQ, LT, LE, GT, GE,

    // end of input
    EOF
}
