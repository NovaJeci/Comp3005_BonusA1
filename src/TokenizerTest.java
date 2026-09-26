import java.util.List;

/**
 * Covers Section 7.1 (tokenizer), cases 1-9, from the assignment.
 * No JUnit dependency -- just run `java TokenizerTest` and read PASS/FAIL.
 *
 * Compile from the project root with:
 *   javac -d out source/*.java tests/*.java
 *   java -cp out TokenizerTest
 */
public class TokenizerTest {

    private static int passed = 0;
    private static int failed = 0;

    public static void main(String[] args) {
        // case 1: no whitespace anywhere
        expectTypes("select[x1=3](R)",
                TokenType.SELECT, TokenType.LBRACKET, TokenType.IDENT, TokenType.EQ,
                TokenType.NUMBER, TokenType.RBRACKET, TokenType.LPAREN, TokenType.IDENT,
                TokenType.RPAREN, TokenType.EOF);

        // case 2: same query, with whitespace -- must produce the identical token TYPES
        expectTypes("select[ x1 = 3 ](R)",
                TokenType.SELECT, TokenType.LBRACKET, TokenType.IDENT, TokenType.EQ,
                TokenType.NUMBER, TokenType.RBRACKET, TokenType.LPAREN, TokenType.IDENT,
                TokenType.RPAREN, TokenType.EOF);

        // case 3: one GE token, not GT followed by EQ
        expectTypes("select[Age>=30](R)",
                TokenType.SELECT, TokenType.LBRACKET, TokenType.IDENT, TokenType.GE,
                TokenType.NUMBER, TokenType.RBRACKET, TokenType.LPAREN, TokenType.IDENT,
                TokenType.RPAREN, TokenType.EOF);

        // case 4: GT then NUMBER(-30) -- no invented ">-" operator
        List<Token> case4 = expectTypes("select[Age>-30](R)",
                TokenType.SELECT, TokenType.LBRACKET, TokenType.IDENT, TokenType.GT,
                TokenType.NUMBER, TokenType.RBRACKET, TokenType.LPAREN, TokenType.IDENT,
                TokenType.RPAREN, TokenType.EOF);
        expectText("case 4 number text", case4.get(4).text, "-30");

        // case 5: parenthesis inside a quoted string must not confuse the scanner
        List<Token> case5 = expectTypes("select[Name='Bob)'](R)",
                TokenType.SELECT, TokenType.LBRACKET, TokenType.IDENT, TokenType.EQ,
                TokenType.STRING, TokenType.RBRACKET, TokenType.LPAREN, TokenType.IDENT,
                TokenType.RPAREN, TokenType.EOF);
        expectText("case 5 string text", case5.get(4).text, "Bob)");

        // case 6: comma inside a quoted string must not separate anything
        List<Token> case6 = expectTypes("select[Name='a,b'](R)",
                TokenType.SELECT, TokenType.LBRACKET, TokenType.IDENT, TokenType.EQ,
                TokenType.STRING, TokenType.RBRACKET, TokenType.LPAREN, TokenType.IDENT,
                TokenType.RPAREN, TokenType.EOF);
        expectText("case 6 string text", case6.get(4).text, "a,b");

        // case 7: doubled quote decodes to one literal quote character
        List<Token> case7 = expectTypes("select[Name='O''Brien'](R)",
                TokenType.SELECT, TokenType.LBRACKET, TokenType.IDENT, TokenType.EQ,
                TokenType.STRING, TokenType.RBRACKET, TokenType.LPAREN, TokenType.IDENT,
                TokenType.RPAREN, TokenType.EOF);
        expectText("case 7 string text", case7.get(4).text, "O'Brien");

        // case 8: an attribute spelled like a keyword still tokenizes as that keyword;
        // the PARSER is responsible for accepting it as an attribute name (see Tokenizer's
        // class comment and GRAMMAR.md section 1.1).
        expectTypes("select[union=3](R)",
                TokenType.SELECT, TokenType.LBRACKET, TokenType.UNION, TokenType.EQ,
                TokenType.NUMBER, TokenType.RBRACKET, TokenType.LPAREN, TokenType.IDENT,
                TokenType.RPAREN, TokenType.EOF);

        // case 9: unterminated string -> LexicalException with a position, never a crash
        expectLexicalError("select[Name='Bob](R)");

        System.out.println();
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) {
            System.exit(1);
        }
    }

    private static List<Token> expectTypes(String source, TokenType... expected) {
        List<Token> tokens = new Tokenizer(source).tokenizeAll();
        boolean ok = tokens.size() == expected.length;
        if (ok) {
            for (int i = 0; i < expected.length; i++) {
                if (tokens.get(i).type != expected[i]) {
                    ok = false;
                    break;
                }
            }
        }
        report(ok, "tokenize(\"" + source + "\")", describe(expected), describeTokens(tokens));
        return tokens;
    }

    private static void expectText(String label, String actual, String expected) {
        report(actual.equals(expected), label, expected, actual);
    }

    private static void expectLexicalError(String source) {
        try {
            new Tokenizer(source).tokenizeAll();
            report(false, "tokenize(\"" + source + "\")", "LexicalException", "no exception thrown");
        } catch (LexicalException e) {
            report(true, "tokenize(\"" + source + "\")", "LexicalException", e.getMessage());
        }
    }

    private static void report(boolean ok, String label, String expected, String actual) {
        if (ok) {
            passed++;
            System.out.println("PASS  " + label);
        } else {
            failed++;
            System.out.println("FAIL  " + label);
            System.out.println("      expected: " + expected);
            System.out.println("      actual:   " + actual);
        }
    }

    private static String describe(TokenType[] types) {
        StringBuilder sb = new StringBuilder();
        for (TokenType t : types) {
            sb.append(t).append(' ');
        }
        return sb.toString().trim();
    }

    private static String describeTokens(List<Token> tokens) {
        StringBuilder sb = new StringBuilder();
        for (Token t : tokens) {
            sb.append(t.type).append(' ');
        }
        return sb.toString().trim();
    }
}
