import java.util.ArrayList;
import java.util.List;

/**
 * Recursive descent parser, one method per grammar rule in GRAMMAR.md.
 * Holds one token of lookahead ("current") from the Tokenizer.
 *
 * IMPORTANT: the grammar in GRAMMAR.md is written left-recursively
 * (e.g. Expr ::= Expr ("union"|"minus") Term | Term) because that's the
 * natural way to WRITE a left-associative rule. A recursive descent
 * parser can't implement left recursion directly -- parseExpr() calling
 * itself before consuming any input would recurse forever. So every
 * left-recursive rule here is mechanically rewritten into an equivalent
 * loop: parse one Term, then keep folding in (operator, Term) pairs for
 * as long as the operator keeps appearing. See parseExpr/parseTerm/
 * parseFactor/parseOrCond/parseAndCond below -- they're all the same
 * shape for exactly this reason.
 *
 * parseNotCond's "not" is right-recursive instead ("not" NotCond), which
 * needs no such rewrite: the recursive call is the last thing done, and
 * it's preceded by consuming the "not" token, so it terminates normally.
 */
public final class Parser {
    private final Tokenizer tokenizer;
    private Token current;

    public Parser(String source) {
        this.tokenizer = new Tokenizer(source);
        this.current = tokenizer.next();
    }

    /** Parses one full query and rejects anything left over afterwards. */
    public Expr parseQuery() {
        Expr expr = parseExpr();
        if (current.type != TokenType.EOF) {
            throw new SyntaxException(
                    "unexpected '" + current.text + "' after end of query", current.position);
        }
        return expr;
    }

    // ---------------------------------------------------------------
    // Expr ::= Expr ("union" | "minus") Term | Term      (precedence level 1, lowest)
    // ---------------------------------------------------------------
    private Expr parseExpr() {
        Expr node = parseTerm();
        while (current.type == TokenType.UNION || current.type == TokenType.MINUS) {
            Expr.BinaryKind kind =
                    current.type == TokenType.UNION ? Expr.BinaryKind.UNION : Expr.BinaryKind.MINUS;
            advance();
            Expr rhs = parseTerm();
            node = new Expr.BinaryOp(kind, node, rhs); // left-associative: node becomes the new left side
        }
        return node;
    }

    // ---------------------------------------------------------------
    // Term ::= Term "intersect" Factor | Factor           (precedence level 2)
    // ---------------------------------------------------------------
    private Expr parseTerm() {
        Expr node = parseFactor();
        while (current.type == TokenType.INTERSECT) {
            advance();
            Expr rhs = parseFactor();
            node = new Expr.BinaryOp(Expr.BinaryKind.INTERSECT, node, rhs);
        }
        return node;
    }

    // ---------------------------------------------------------------
    // Factor ::= Factor "times" Primary
    //          | Factor "join" "[" Condition "]" Primary
    //          | Primary                                  (precedence level 3, highest binary)
    // ---------------------------------------------------------------
    private Expr parseFactor() {
        Expr node = parsePrimary();
        while (current.type == TokenType.TIMES || current.type == TokenType.JOIN) {
            if (current.type == TokenType.TIMES) {
                advance();
                Expr rhs = parsePrimary();
                node = new Expr.BinaryOp(Expr.BinaryKind.TIMES, node, rhs);
            } else {
                advance(); // consume "join"
                expect(TokenType.LBRACKET, "'['");
                Condition cond = parseCondition();
                expect(TokenType.RBRACKET, "']'");
                Expr rhs = parsePrimary();
                node = new Expr.JoinOp(cond, node, rhs);
            }
        }
        return node;
    }

    // ---------------------------------------------------------------
    // Primary ::= "select"  "[" Condition "]" "(" Expr ")"
    //           | "project" "[" AttrRefList "]" "(" Expr ")"
    //           | "rename"  "[" IDENT "]" "(" Expr ")"
    //           | "(" Expr ")"
    //           | IDENT
    // ---------------------------------------------------------------
    private Expr parsePrimary() {
        switch (current.type) {
            case SELECT: {
                advance();
                expect(TokenType.LBRACKET, "'['");
                Condition cond = parseCondition();
                expect(TokenType.RBRACKET, "']'");
                expect(TokenType.LPAREN, "'('");
                Expr input = parseExpr();
                expect(TokenType.RPAREN, "')'");
                return new Expr.SelectOp(cond, input);
            }
            case PROJECT: {
                advance();
                expect(TokenType.LBRACKET, "'['");
                List<Operand.AttrRef> attrs = parseAttrRefList();
                expect(TokenType.RBRACKET, "']'");
                expect(TokenType.LPAREN, "'('");
                Expr input = parseExpr();
                expect(TokenType.RPAREN, "')'");
                return new Expr.ProjectOp(attrs, input);
            }
            case RENAME: {
                advance();
                expect(TokenType.LBRACKET, "'['");
                String newName = expectName("a relation name");
                expect(TokenType.RBRACKET, "']'");
                expect(TokenType.LPAREN, "'('");
                Expr input = parseExpr();
                expect(TokenType.RPAREN, "')'");
                return new Expr.RenameOp(newName, input);
            }
            case LPAREN: {
                advance();
                Expr inner = parseExpr();
                expect(TokenType.RPAREN, "')'");
                return inner; // parentheses just override precedence; they add no node
            }
            case IDENT: {
                String name = current.text;
                advance();
                return new Expr.RelationRef(name);
            }
            default:
                throw new SyntaxException(
                        "expected a relation, '(', or select/project/rename here, but found '"
                                + current.text + "'",
                        current.position);
        }
    }

    // ---------------------------------------------------------------
    // AttrRefList ::= AttrRef { "," AttrRef }
    // AttrRef     ::= [ IDENT "." ] IDENT
    // ---------------------------------------------------------------
    private List<Operand.AttrRef> parseAttrRefList() {
        if (current.type == TokenType.RBRACKET) {
            throw new SyntaxException("attribute list cannot be empty", current.position);
        }
        List<Operand.AttrRef> attrs = new ArrayList<>();
        attrs.add(parseAttrRef());
        while (current.type == TokenType.COMMA) {
            advance();
            attrs.add(parseAttrRef());
        }
        return attrs;
    }

    private Operand.AttrRef parseAttrRef() {
        String first = expectName("an attribute name");
        if (current.type == TokenType.DOT) {
            advance();
            String second = expectName("an attribute name");
            return new Operand.AttrRef(first, second);
        }
        return new Operand.AttrRef(null, first);
    }

    // ---------------------------------------------------------------
    // Condition ::= OrCond                                 (delegates straight through)
    // OrCond    ::= OrCond "or" AndCond | AndCond           (precedence level 1, lowest)
    // AndCond   ::= AndCond "and" NotCond | NotCond         (precedence level 2)
    // NotCond   ::= "not" NotCond | "(" Condition ")" | Comparison   (level 3)
    // Comparison::= Operand CompOp Operand                  (level 4, highest)
    // ---------------------------------------------------------------
    private Condition parseCondition() {
        return parseOrCond();
    }

    private Condition parseOrCond() {
        Condition node = parseAndCond();
        while (current.type == TokenType.OR) {
            advance();
            Condition rhs = parseAndCond();
            node = new Condition.Or(node, rhs);
        }
        return node;
    }

    private Condition parseAndCond() {
        Condition node = parseNotCond();
        while (current.type == TokenType.AND) {
            advance();
            Condition rhs = parseNotCond();
            node = new Condition.And(node, rhs);
        }
        return node;
    }

    private Condition parseNotCond() {
        if (current.type == TokenType.NOT) {
            advance();
            Condition inner = parseNotCond(); // right-recursive: no left-recursion rewrite needed
            return new Condition.Not(inner);
        }
        if (current.type == TokenType.LPAREN) {
            advance();
            Condition inner = parseCondition();
            expect(TokenType.RPAREN, "')'");
            return inner;
        }
        return parseComparison();
    }

    private Condition parseComparison() {
        Operand left = parseOperand();
        TokenType op = current.type;
        if (!isComparisonOp(op)) {
            throw new SyntaxException(
                    "expected a comparison operator (=, !=, <, <=, >, >=) here, but found '"
                            + current.text + "'",
                    current.position);
        }
        advance();
        Operand right = parseOperand();
        return new Condition.Comparison(left, op, right);
    }

    private static boolean isComparisonOp(TokenType t) {
        return t == TokenType.EQ || t == TokenType.NEQ || t == TokenType.LT
                || t == TokenType.LE || t == TokenType.GT || t == TokenType.GE;
    }

    // ---------------------------------------------------------------
    // Operand ::= AttrRef | NUMBER | STRING
    // ---------------------------------------------------------------
    private Operand parseOperand() {
        if (current.type == TokenType.NUMBER) {
            Operand.NumberLit lit = new Operand.NumberLit(current.text);
            advance();
            return lit;
        }
        if (current.type == TokenType.STRING) {
            Operand.StringLit lit = new Operand.StringLit(current.text);
            advance();
            return lit;
        }
        return parseAttrRef();
    }

    // ---------------------------------------------------------------
    // token helpers
    // ---------------------------------------------------------------

    /**
     * True for IDENT and every keyword. This is what lets a keyword double
     * as an attribute name (case 8: select[union=3](R)) -- the TOKENIZER
     * always emits "union" as a UNION token, and it's this parser-level
     * check, used only where a name is actually expected (attribute refs,
     * a rename target), that reinterprets it as the text "union".
     */
    private static boolean isNameLike(TokenType t) {
        switch (t) {
            case IDENT:
            case UNION:
            case INTERSECT:
            case MINUS:
            case TIMES:
            case JOIN:
            case SELECT:
            case PROJECT:
            case RENAME:
            case AND:
            case OR:
            case NOT:
                return true;
            default:
                return false;
        }
    }

    private String expectName(String what) {
        if (!isNameLike(current.type)) {
            throw new SyntaxException(
                    "expected " + what + " here, but found '" + current.text + "'",
                    current.position);
        }
        String text = current.text;
        advance();
        return text;
    }

    private void expect(TokenType type, String what) {
        if (current.type != type) {
            throw new SyntaxException(
                    "expected " + what + " here, but found '" + current.text + "'",
                    current.position);
        }
        advance();
    }

    private void advance() {
        current = tokenizer.next();
    }
}
