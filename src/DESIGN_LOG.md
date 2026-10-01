# DESIGN_LOG.md

## [Sept 23, 2026] — Reading and planning

Spent time reading about context-free grammars, EBNF, and recursive
descent parsing before writing any code, per the assignment's own
two-week plan (Days 1–2). Spent an hour with the Relax system to see how a working system behaves
before designing my own.

## [Sept 23, 2026] — Grammar design (GRAMMAR.md)

Worked through the precedence and associativity decisions for the
relational operators: union and minus at the same precedence level,
left-associative; intersect binding tighter, mirroring how `and` binds
tighter than `or`; times and join binding tightest of all. Worked through
the ambiguity demonstration for `A union B minus C` using the naive
grammar given in the assignment — drew both parse trees, then found a
concrete instance (A={1,2}, B={2,3}, C={2}) where the two groupings
produce genuinely different results ({1,3} vs {1,2,3}), which is what
makes the ambiguity a real problem and not just a style question. Wrote
the stratified (left-recursive) grammar that forces the grouping I
decided on.

## [Sept 26, 2026] — Tokenizer

Built the hand-written tokenizer: maximal munch for `>=`, `<=`, `!=`;
quoted-string scanning that doesn't care about parentheses or commas
inside quotes; doubled-quote escaping. Decided to make keywords
"contextual" — `union` always tokenizes as a UNION token, but the parser
is what accepts a keyword token as an attribute name later — rather than
building a context-sensitive lexer, to keep the tokenizer itself
context-free. Verified all 9 tokenizer test cases by actually compiling
and running them, not just reading the code — in particular case 4
(`Age>-30`) needed checking that the scanner doesn't try to invent a `>-`
operator, since there's no subtraction symbol in this language at all
(relational minus is spelled as a keyword), which is what makes a hyphen
unambiguous as always being the start of a number.

## [Sept 26, 2026] — Parser and parse tree printer

Implemented the recursive-descent parser, one method per precedence
level. The grammar in GRAMMAR.md is written left-recursively because
that's the natural way to express a left-associative rule, but recursive
descent can't implement left recursion directly — had to rewrite every
left-recursive rule into an iterative loop (parse one Term, then fold in
(operator, Term) pairs for as long as the operator appears) rather than
a recursive call on the left. Built the tree printer and checked its
output against the exact example string given in Section 6.2 of the
assignment character-for-character, rather than assuming my indentation
logic was right from reading it.

**AI assistance issue #1 — found by running the code, not by reading it:**
The tree printer uses Unicode box-drawing characters (`└──`, `├──`, `│`)
for the tree connectors. When this was first run in a different terminal
environment, the connectors printed as `?` instead of the correct
characters. The actual bytes being written were correct — a hex dump
confirmed valid UTF-8 — but the terminal's default stdout encoding was
plain ASCII, which silently mangled the display. This wasn't something
that showed up by inspecting the code; it only appeared by actually
running `Main --tree` and looking at the output. Fixed by explicitly
wrapping `System.out` in a UTF-8 `PrintStream` in `Main.java`, rather
than relying on the platform's default encoding.

## [Sept 27, 2026] — Relation loading and the six operators

Decided to load relation definitions with a separate, line-based reader
(`RelationLoader`) rather than extending the query `Tokenizer`/`Parser`.
The query language is whitespace-insensitive by design, but a relation
definition's tuples are separated by a bare newline with no comma or
other token in between — making one tokenizer newline-sensitive only
inside `{ }` blocks would need lexer modes the rest of the grammar never
needs, so a small dedicated reader for just that part of the syntax was
simpler to write and to defend.

Designed every schema column to always carry the name of the relation
that currently "owns" it (its qualifier), set once at load time and
replaced by `rename`. This is what makes `times`/`join` collision
detection work, and it's specifically why the self join in case 20
(`rename[E2](Emp) join[Emp.MgrID=E2.EID] Emp`) needs the rename at all —
without it, both sides of the join would still be qualified "Emp" and
every column would collide with itself.

**AI assistance issue #2 — caught during design, before running anything:**
The first plan for implementing `join[cond]` was to literally compose it
as `times()` followed by `select(cond)`, matching the assignment's stated
definition in Section 4.3 exactly. But the instrumentation requirement in
Section 8.2 wants separate counters for select and join — composing join
out of select would have made the join's condition-evaluations land in
the *select* counter, conflating the two and making the Section 8
performance numbers meaningless. This was caught by thinking through what
the counters would actually measure before implementing it, and fixed by
writing join as its own single nested loop with its own counter, while
keeping the same output as times+select would produce.

Chose `BigDecimal` over `double` for all numeric values specifically to
avoid binary floating-point equality surprises (like 0.1+0.2 != 0.3)
silently breaking tuple deduplication and set operations, which rely on
exact equality.

Decided `project[Name, Name](R)` (case 24) should be a schema error
rather than silently collapsing to one column — a duplicate column in a
schema would make every later reference to it ambiguous, which conflicts
with how attribute resolution works everywhere else in the system.

## [Sept 28, 2026] — Data generator and instrumentation

Built `DataGenerator` to produce R(a,b) and S(b,c) with a controllable
match rate by assigning the join attribute randomly over a domain sized
to hit the target match rate on average.

**AI assistance issue #3 — looked like a bug, turned out to be correct
behavior, found by actually checking rather than assuming either way:**
While testing `project[b](R)` on generated data with match rate 1.0 (where
`b` was expected to be close to unique per tuple), the result had only
622 distinct values out of 1000 tuples. This looked like a real bug —
possibly in `Value.NumValue`'s equals/hashCode causing false collisions.
Wrote an isolated test inserting 1000 distinct `BigDecimal`-backed values
into a `HashSet` directly, which came back with exactly 1000 distinct
entries and no collisions, ruling out the value-equality code. Traced it
instead to the generator's domain assignment, which assigns the join
attribute *randomly* rather than round-robin. Checked the math: drawing n
values with replacement from a domain of size n leaves about
`1 - 1/e ≈ 63.2%` of them distinct, by the birthday paradox — and 622/1000
is almost exactly that. So the "bug" was actually expected statistical
behavior of random sampling, not a defect, and the only way to tell the
difference was to actually test the suspect component in isolation rather
than guessing from the symptom alone.

## [Sept 29, 2026] — Error handling

Confirmed all five required error categories are covered end to end:
lexical (`LexicalException`), syntax (`SyntaxException`), name
(`NameException`), schema (`SchemaException`), and type
(`TypeException`) — each caught at the top level in `Main.java` and
printed as a clean message, never a raw stack trace. Tested each category
directly: an unterminated string, a missing closing parenthesis, an
unknown relation, a union of incompatible schemas, and a number-vs-string
comparison.

## [Sept 29, 2026] — Performance study

Ran `PerformanceHarness` at the required sizes (1,000 through 64,000) and
confirmed the join's comparison count matches `n × m` exactly at every
single size — not approximately, exactly. Separately ran the join at
several different match rates at a fixed size and confirmed the
comparison count stayed completely constant while the output size varied
by two orders of magnitude, which is direct evidence that the nested loop
examines every pair unconditionally regardless of the data. Fit a
log-log regression to the wall-time data and found the two smallest sizes
(1000, 2000) pulled the slope down compared to the rest of the data —
attributed this to JVM warm-up (the JIT hasn't optimized the hot loop yet
at that point), and used the slope from the larger sizes (≈1.93) as the
more reliable estimate of the algorithm's growth rate.

## [Sept 30, 2026] — README, GRAMMAR.md finalization, and cleanup

Finished `GRAMMAR.md`'s sources section and reread the whole grammar
document now that the parser's actual behavior is fully settled, to make
sure the documented precedence table and the implemented parser still
agree with each other. Wrote `README.md` covering how to run every tool
in the project (`Main`, the four test suites, `DataGenerator`,
`PerformanceHarness`, `SelectProjectHarness`, `MatchRateTest`) and the
known, deliberate scope limitations (no nulls, no query optimization, no
indexes — all explicitly out of scope per Section 3 of the assignment).
