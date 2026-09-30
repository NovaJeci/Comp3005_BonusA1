# Relational Algebra Query Processor

A hand-built relational algebra engine: tokenizer → parser → parse tree →
operator evaluation, plus a data generator and instrumented performance
study. Built for COMP 3005 Bonus Project 1.

## How to run it

Everything is plain Java (no build tool, no external dependencies).

**Compile:**
```
javac -d out source/*.java tests/*.java
```

**Print a parse tree without executing anything:**
```
java -cp out Main --tree "project[Name](select[Age>30](Employees))"
```

**Load relations from a file and run a query against them:**
```
java -cp out Main --run sample-data/employees.txt "project[Name,Age](select[DID='D1'](Employees))"
```
The relations file uses the syntax from Section 4.1 of the assignment
(see `sample-data/employees.txt` for a working example). The result prints
as a table, followed by the select/join comparison counters for that run.

**Run the test suites** (cases 1–25 from the assignment, plus extras):
```
java -cp out TokenizerTest
java -cp out ParserTest
java -cp out RelationLoaderTest
java -cp out EvaluatorTest
```
All four should print `... passed, 0 failed`.

**Generate test data** (Section 8.1):
```
java -cp out DataGenerator <n> <m> <matchRate> <outputPrefix>
```
Writes `<outputPrefix>-R.txt` and `<outputPrefix>-S.txt`, e.g.
`java -cp out DataGenerator 1000 1000 1.0 /tmp/demo` writes
`/tmp/demo-R.txt` and `/tmp/demo-S.txt`.

**Run the performance experiment** (Section 8.3 - this is slow at the top
end by design; 64000×64000 is 4.096 billion comparisons):
```
java -cp out PerformanceHarness            # match rate 1.0
java -cp out PerformanceHarness 5.0        # custom match rate
```
Prints a Markdown table (n, m, comparisons, wall time, output tuples)
straight to stdout, ready to paste into `REPORT.md`.

**Measure select/project scaling** (Section 8.4, Question 3):
```
java -cp out SelectProjectHarness
```

**Measure the effect of match rate on comparisons vs wall time** (Section
8.4, Question 5):
```
java -cp out MatchRateTest
```

## What's supported

- Full query language from Section 4.2: `select`, `project`, `rename`,
  `union`, `intersect`, `minus`, `times`, `join[cond]`, with the precedence
  and associativity documented in `GRAMMAR.md`.
- Relation definitions from Section 4.1: named relations, attribute lists,
  set-semantics tuples (duplicates collapse), quoted string values
  (including commas/parens/spaces inside quotes and doubled-quote
  escaping), comments (`//`), and blank lines.
- Hand-written tokenizer and recursive-descent parser - no regex, no
  parser generator, no `eval`/`exec`.
- A `--tree` parse-tree printer matching the format in Section 6.2.
- All six operators executing against real data, with correct output
  schemas (Section 4.3), including qualified-name tracking so `times`/
  `join` collisions and self-joins (Section 7.3, case 20) work correctly.
- Five error categories, all reported as a clean message rather than a
  stack trace: lexical (`LexicalException`), syntax (`SyntaxException`),
  name (`NameException`), schema (`SchemaException`), and type
  (`TypeException`).
- Instrumented `select`/`join` operators (comparison counters) and a data
  generator with a controllable match rate, used for the performance
  study in `REPORT.md`.

## Known limitations / deliberate scope decisions

- **Relation definitions are parsed by a separate, line-based reader**
  (`RelationLoader`), not by the query `Tokenizer`/`Parser`. Tuple lines
  are separated by a bare newline with no comma or other token between
  them, which the whitespace - insensitive query grammar has no way to
  express without lexer modes. This is a deliberate design decision - see
  the comment at the top of `RelationLoader.java` and the corresponding
  note in `GRAMMAR.md`.
- **Numbers are stored as `BigDecimal`**, not `double`, specifically to
  avoid floating-point equality surprises affecting tuple deduplication
  and set operations.
- **`project[Name, Name](R)` is a schema error**, not a silently collapsed
  single column (Section 7.3, case 24) - a documented choice, not an
  oversight; see the comment in `Evaluator.projectOp`.
- **No nulls, no query optimization, no indexes, no hash/sort-merge
  join** - all explicitly out of scope for this component (Section 3).
  The join is a plain nested loop; `REPORT.md` Question 6 discusses what
  would need to change to make it scale.
- **No disk-based storage** - everything is loaded into memory, per
  Section 3.

## Project layout

```
source/    tokenizer, parser, AST, evaluator, data generator, CLI
tests/     test suites for cases 1-25, plus RelationLoaderTest
sample-data/   example relations file matching Section 4.1
GRAMMAR.md     grammar, precedence, ambiguity demo, parsing strategy
REPORT.md      performance study and analysis
DESIGN_LOG.md  dated work-session log
```
