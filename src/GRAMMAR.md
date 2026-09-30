# GRAMMAR.md — Relational Algebra Query Processor

## 1. The grammar (EBNF)

### 1.1 Lexical grammar (tokens)

```
IDENT    ::= letter { letter | digit | "_" }
NUMBER   ::= [ "-" ] digit { digit } [ "." digit { digit } ]
STRING   ::= "'" { any-char-except-quote | "''" } "'"
           | bare-string        (* no comma, space, '(', ')', or quote inside *)
```

A doubled single quote `''` inside a quoted string denotes one literal quote
character (case 7). An unterminated quoted string is a lexical error (case 9).

Keywords: `union intersect minus times join select project rename and or not`.
These are **contextual keywords**, not fully reserved words: the tokenizer
always emits them as keyword tokens, but the parser accepts a keyword token
wherever an `Operand` (attribute reference) is grammatically expected and
treats its text as an identifier there. This resolves case 8
(`select[union=3](R)`) without a separate identifier-vs-keyword pass in the
lexer. This rule is documented here because it lives at the boundary between
lexer and parser and must be applied consistently by both.

### 1.2 Relation definitions

```
Program        ::= { RelationDef } [ Query ]

RelationDef    ::= IDENT "(" AttrNameList ")" "=" "{" { Tuple } "}"
AttrNameList   ::= IDENT { "," IDENT }
Tuple          ::= Value { Value }
Value          ::= NUMBER | STRING
```

A tuple's arity must equal the relation's declared attribute count; this is
a semantic check (schema error), not something the context-free grammar
enforces syntactically. Comment lines (`//...`) and blank lines are stripped
before parsing, not part of this grammar.

### 1.3 Queries — relational expressions

```
Query          ::= Expr

Expr           ::= Expr ( "union" | "minus" ) Term
                 | Term

Term           ::= Term "intersect" Factor
                 | Factor

Factor         ::= Factor "times" Primary
                 | Factor "join" "[" Condition "]" Primary
                 | Primary

Primary        ::= "select"  "[" Condition "]" "(" Expr ")"
                 | "project" "[" AttrRefList "]" "(" Expr ")"
                 | "rename"  "[" IDENT "]" "(" Expr ")"
                 | "(" Expr ")"
                 | IDENT

AttrRefList    ::= AttrRef { "," AttrRef }
AttrRef        ::= [ IDENT "." ] IDENT
```

`project[]( ... )` is rejected: `AttrRefList` requires at least one `AttrRef`
(case 17).

### 1.4 Conditions

```
Condition      ::= OrCond

OrCond         ::= OrCond "or" AndCond
                 | AndCond

AndCond        ::= AndCond "and" NotCond
                 | NotCond

NotCond        ::= "not" NotCond
                 | "(" Condition ")"
                 | Comparison

Comparison     ::= Operand CompOp Operand
CompOp         ::= "=" | "!=" | "<" | "<=" | ">" | ">="
Operand        ::= AttrRef | NUMBER | STRING
```

## 2. Precedence and associativity

### 2.1 Relational (`Expr`) operators — low to high

| Level | Operators | Associativity | Grammar rule enforcing it |
|---|---|---|---|
| 1 (lowest) | `union`, `minus` | left | `Expr ::= Expr ("union"\|"minus") Term \| Term` |
| 2 | `intersect` | left | `Term ::= Term "intersect" Factor \| Factor` |
| 3 (highest) | `times`, `join[...]` | left | `Factor ::= Factor "times" Primary \| Factor "join[...]" Primary \| Primary` |

`union` and `minus` sit at the **same** precedence level and are left-
associative with each other — see the ambiguity demo below for why this
matters. `select`, `project` and `rename` are not really "operators" in the
precedence sense: their argument is always delimited by an explicit `( )`
pair immediately after the `[...]` parameter, so they behave as primaries
(like a function call), never participating in a precedence conflict.

**Design decision:** `A union B minus C` groups as `(A union B) minus C`.
`A minus B minus C` groups as `(A minus B) minus C` (left-to-right), matching
ordinary left-associative reading. `intersect` binds tighter than
`union`/`minus`, mirroring the usual analogy between `intersect`/`and`/`*`
and `union`/`or`/`+`. `times`/`join` bind tighter still, since they change
the schema (they are structurally closer to a "primary" combination step
than a filtering/set step).

### 2.2 Condition operators — low to high

| Level | Operators | Associativity |
|---|---|---|
| 1 (lowest) | `or` | left |
| 2 | `and` | left |
| 3 | `not` | right (prefix) |
| 4 (highest) | comparison (`= != < <= > >=`) | non-associative |

This gives case 12, `not (a=1 and b=2) or c>3`, the grouping
`(not (a=1 and b=2)) or (c>3)`, and case 13, `a=1 and b=2 or c=3`, the
grouping `(a=1 and b=2) or c=3`.

## 3. Ambiguity demonstration

The naive grammar given in the assignment:

```
Expr ::= Expr "union" Expr
       | Expr "minus" Expr
       | "(" Expr ")"
       | IDENT
```

is ambiguous for `A union B minus C` because it gives no rule for which
`Expr` a given token attaches to. Two distinct parse trees exist:

**Tree 1 — left grouping:** `(A union B) minus C`

```
        minus
       /     \
    union     C
   /     \
  A       B
```

**Tree 2 — right grouping:** `A union (B minus C)`

```
    union
   /     \
  A     minus
       /     \
      B       C
```

**Concrete instance where they differ.** Let:

- A = {1, 2}
- B = {2, 3}
- C = {2}

Tree 1: `(A union B) minus C` = `{1, 2, 3} minus {2}` = **{1, 3}**

Tree 2: `A union (B minus C)` = `{1, 2} union {3}` = **{1, 2, 3}**

The two trees give different relations, so the grammar is genuinely
ambiguous, not just "differently written."

**Stratified (unambiguous) grammar:**

```
Expr ::= Expr ("union" | "minus") Term
       | Term
Term ::= "(" Expr ")"
       | IDENT
```

Because `Expr` recurses only on its left side, this forces left-to-right
grouping for any chain of `union`/`minus`, i.e. it always forces **Tree 1**
for `A union B minus C`. This is the grammar actually implemented in
Section 1.3 above (with `Term` further expanded into the full precedence
chain down to `Primary`).

## 4. Parsing strategy justification

**Strategy: recursive descent**, one function per nonterminal
(`parseExpr`, `parseTerm`, `parseFactor`, `parsePrimary`,
`parseOrCond`, `parseAndCond`, `parseNotCond`, `parseComparison`), plus a
hand-written tokenizer feeding it one token of lookahead.

**Why:** the language has no need for a general parser — every rule can be
chosen by looking at the current token (`select`/`project`/`rename`/`(`/
`IDENT` at the start of a `Primary`; the operator keyword between
recursive calls). Recursive descent maps directly onto the precedence
table in Section 2 (one parsing function per precedence level), which
makes it easy to point at "the exact place" the precedence and
associativity decisions live, as required by this document.

**Left recursion.** The grammar in Sections 1.3–1.4 is *left-recursive* as
written (`Expr ::= Expr "union" Term | Term`, etc.), and a naive recursive
descent implementation of that rule recurses into `parseExpr` before
consuming any input, which never terminates. Every left-recursive rule
above is mechanically rewritten into an equivalent iterative loop before
implementation, e.g.:

```
parseExpr():
    node = parseTerm()
    while current token is "union" or "minus":
        op = consume()
        rhs = parseTerm()
        node = BinaryOp(op, node, rhs)
    return node
```

This produces the same left-associative tree as the left-recursive rule,
without recursing on the left. The same transformation is applied to
`Term`, `Factor`, `OrCond`, and `AndCond`. `NotCond`'s `not` is *right*-
recursive (`"not" NotCond`), which recursive descent handles directly with
no transformation needed, since the recursive call is the last thing done
and is preceded by consuming the `not` token.

## 5. Sources
- [1]“EBNF Syntax,” Oracle Help Center, Apr. 28, 2025. https://docs.oracle.com/en/database/other-databases/nosql-database/25.1/sqlreferencefornosql/ebnf-syntax.html (accessed Sept. 23, 2026).
- [2]GeeksforGeeks, “Operator Grammar and Precedence Parser,” GeeksforGeeks, May 03, 2018. https://www.geeksforgeeks.org/compiler-design/operator-grammar-and-precedence-parser-in-toc/ (accessed Sept. 23, 2026).
- [3]GeeksforGeeks, “Ambiguous Grammar,” GeeksforGeeks. Accessed: Sep. 23, 2026. [Online]. Available: https://www.geeksforgeeks.org/compiler-design/ambiguous-grammar/
- [4]M. Bednarski, “Operator Priority and Associativity in EBNF Grammar,” Medium. Accessed: Sep. 30, 2026. [Online]. Available: https://medium.com/@mbednarski/operator-priority-and-associativity-in-ebnf-grammar-3a9f23dd9daf
- [5]Nystrom, Crafting Interpreters - scanning and parsing chapters
- [6]Wikipedia: EBNF, recursive descent parsing, maximal munch, operator-precedence parsing
- [7]GeeksforGeeks, “Operator Precedence and Associativity in Programming,” GeeksforGeeks. Accessed: Sep. 23, 2026. [Online]. Available: https://www.geeksforgeeks.org/c/operator-precedence-and-associativity-in-programming/
- [8]GeeksforGeeks, “Introduction of Relational Algebra in DBMS,” GeeksforGeeks. Accessed: Sep. 30, 2026. [Online]. Available: https://www.geeksforgeeks.org/dbms/introduction-of-relational-algebra-in-dbms/