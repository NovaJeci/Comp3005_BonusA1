# DESIGN_LOG.md

A dated diary of the working sessions. One short entry per session. Each entry says what I was trying to do,
what I tried, and what broke.
---

## Working sessions

### 2026-09-30 (session 1): TODO title, e.g. "Tokenizer"

- **Trying to do:** TODO
- **What I tried:** TODO
- **What broke:** TODO

Hint: think about the tokenizer cases 1-9. Which one did you get wrong first? (maximal munch on `>=`, `>-30`,
the doubled quote in `'O''Brien'`, a parenthesis inside a string, an unterminated string, a keyword used as an
attribute name.)

### 2026-09-30 (session 2): TODO title, e.g. "Parser, tree printer and precedence"

- **Trying to do:** TODO
- **What I tried:** TODO
- **What broke:** TODO

Hint: how did I decide what `A union B minus C` means? What happens to a recursive descent parser if the
rule is written `Expr ::= Expr "union" Term`? Did I hit that, or read about it first?

### 2026-09-30 (session 3): TODO title, e.g. "Operators, schemas and error handling"

- **Trying to do:** TODO
- **What I tried:** TODO
- **What broke:** TODO

Hint: duplicate removal, qualified names after a join, the self join, number against string, the decision for
`project[Name, Name]`. What did I have to fix when the tests first ran?

### 2026-09-30 (session 4): TODO title, e.g. "Data generator, counters and the experiment"

- **Trying to do:** TODO
- **What I tried:** TODO
- **What broke:** TODO

Hint: did the comparison count match n*m on the first run? What surprised me in the timing table (the first
local slope of 1.17, the project slope of 0.65)? How did I explain each?

### TODO date (session 5): TODO title, e.g. "Documentation, report and video"

- **Trying to do:** TODO
- **What I tried:** TODO
- **What broke:** TODO

---

## Occasions where AI assistance gave me something wrong, slow or incomplete

At least three. For each: what the AI gave me, what was wrong with it, and how I found out.
Two or three sentences each is enough. They must be things that really happened to you, and they should agree
with section 5 of GRAMMAR.md.

### 1. TODO short title

- **What the AI gave me:** TODO
- **What was wrong (wrong, slow or incomplete):** TODO
- **How I found out:** TODO (a failing test, a compiler error, reading the grammar chapter, the oral explanation I could not give, ...)

### 2. TODO short title

- **What the AI gave me:** TODO
- **What was wrong (wrong, slow or incomplete):** TODO
- **How I found out:** TODO

### 3. TODO short title

- **What the AI gave me:** TODO
- **What was wrong (wrong, slow or incomplete):** TODO
- **How I found out:** TODO

Hint: good entries are specific. "The first tokenizer it wrote split on whitespace, so `select[x1=3](R)` with no spaces
failed" is specific. "It made some mistakes in the parser" is not. Ideas for where to look, only if they apply to you:
a first-draft tokenizer or parser that used a regular expression or a split call, a left-recursive grammar rule that
would loop forever, a grammar that did not actually remove the ambiguity, a join that built the whole cross product in
memory (impossible at 64000 x 64000), a counter that estimated instead of counted, a report explanation that did not
match your own numbers, code that did not compile the first time.
