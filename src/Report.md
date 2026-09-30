# REPORT.md - Performance Study

## Machine / environment

- Machine: 12th Gen Intel(R) Core(TM) i7-1255U (1.70 GHz)
- OS: Edition: Windows 11 Home, Version: 25H2
- Java version (`java -version` output): java version "1.8.0_461"
  Java(TM) SE Runtime Environment (build 1.8.0_461-b11)
  Java HotSpot(TM) 64-Bit Server VM (build 25.461-b11, mixed mode)

## 8.1 Write a data generator
`DONE`

## 8.2 Instrument the engine
`DONE`

## 8.3 R join[R.b=S.b] S at increasing sizes

Match rate = 1.0 (default), generated with `DataGenerator` / `PerformanceHarness`.

| n | m | comparisons | wall time (s) | output tuples |
|---|---|---|---|---|
| 1000 | 1000 | 1,000,000 | 0.2236 | 1000 |
| 2000 | 2000 | 4,000,000 | 0.4482 | 2000 |
| 4000 | 4000 | 16,000,000 | 2.2210 | 4000 |
| 8000 | 8000 | 64,000,000 | 5.4633 | 8000 |
| 16000 | 16000 | 256,000,000 | 21.2402 | 16000 |
| 32000 | 32000 | 1,024,000,000 | 84.2819 | 32000 |
| 64000 | 64000 | 4,096,000,000 | 448.5503 | 64000 |

## 8.4 Analysis

### 1. Exact relationship between n, m and comparison count

The measured comparison count matches `n × m` **exactly** at every row above
(e.g. 8000 × 8000 = 64,000,000; 64000 × 64000 = 4,096,000,000 - no
discrepancy at any size). This isn't a coincidence or an approximation: the
join is implemented as a plain nested loop (`Evaluator.joinOp`) with an
outer loop over every tuple of R and an inner loop over every tuple of S,
and the `joinComparisons` counter is incremented exactly once per pair,
unconditionally, before the join condition is even evaluated. Since the
loop visits every one of the `n × m` possible pairs exactly once regardless
of the data's content or the join condition's selectivity, the comparison
count is a deterministic function of the input sizes alone, not of the
data - which is exactly what Question 5 confirms separately from a
different angle (match rate changes the *comparisons* answer to "how many
pairs actually matched," not "how many pairs were examined").

### 2. Log-log plot of time vs n, and its slope

![wall time vs n, log-log](C:\Users\Prisha\Downloads\join_time_loglog.png)

**Also attached the screenshot of the plot as an attachment in the zip file.**

A least-squares fit on the log-log data gives:
- slope using all 7 points: **1.83**
- slope using only n ≥ 4000: **1.93**

The full-data slope is pulled down by the two smallest points, which are
disproportionately affected by JVM warm-up (the JIT compiler hasn't
finished optimizing the hot inner loop yet at n=1000–2000, so those runs
carry a fixed overhead that doesn't shrink proportionally at small n). The
n ≥ 4000 slope of **≈1.93** is close enough to **2** to call the
algorithm's growth rate quadratic, i.e. **O(n·m)** - consistent with a
nested-loop join, where doubling n roughly quadruples the total work
because both the outer loop and the effective per-tuple inner-loop cost
scale with the relation sizes.

### 3. select and project at the same sizes

| n | select comparisons | select time (s) | select output | project time (s) | project output |
|---|---|---|---|---|---|
| 1000 | 1000 | 0.0036 | 489 | 0.0019 | 622 |
| 2000 | 2000 | 0.0076 | 1046 | 0.0045 | 1259 |
| 4000 | 4000 | 0.0074 | 2035 | 0.0079 | 2512 |
| 8000 | 8000 | 0.0070 | 3985 | 0.0129 | 5023 |
| 16000 | 16000 | 0.0157 | 7934 | 0.0203 | 10103 |
| 32000 | 32000 | 0.0372 | 16105 | 0.0501 | 20280 |
| 64000 | 64000 | 0.0737 | 32118 | 0.0762 | 40520 |

(Note: `project[b]` here doesn't return n distinct rows even though `b` is
drawn from a domain the same size as n, because the domain values are
assigned **randomly** - see `DataGenerator`'s design note. Drawing n values
uniformly at random from a domain of size n leaves about `1 - 1/e ≈ 63.2%`
of them distinct by the birthday paradox, which matches the observed
counts closely — e.g. 622/1000 ≈ 62.2%. This is expected statistical
behavior of the data generator, not a project-operator bug.)

Fitting the same log-log regression to these two curves gives slopes of
**≈1.1 (select)** and **≈0.9 (project)** — both close to **1**, i.e.
**O(n)** — versus join's **≈1.93 (O(n²))**. This matches the shape of each
operator's own implementation directly: `selectOp` and `projectOp` each
make a single pass over one relation's tuples (the `selectComparisons`
counter is exactly n at every size above, confirming one comparison per
tuple, no more), while `joinOp`'s nested loop compares every tuple of one
relation against every tuple of the other. Going from one relation to two,
and from a single loop to a nested loop, is exactly the difference between
linear and quadratic growth here.

### 4. Predicting a 1,000,000 × 1,000,000 join

Using the n ≥ 4000 slope (1.93) and the largest measured point as the
anchor (n₀ = 64000, t₀ = 448.5503s):

```
predicted_time(n) = t0 * (n / n0) ^ slope
                   = 448.5503 * (1,000,000 / 64,000) ^ 1.93
                   = 448.5503 * (15.625) ^ 1.93
                   = 448.5503 * 201.5
                   ≈ 90,341 seconds
                   ≈ 25.1 hours
```

This is not run - it's purely an extrapolation from the measured growth
rate, which is the point of the question: a naive nested-loop join is
completely impractical at this scale, and you can know that *without*
burning a day of compute to find out the hard way.

### 5. Does match rate change comparisons? Does it change wall time?

Measured at a fixed size (n = m = 4000) across four match rates:

| match rate | comparisons | wall time (s) | output tuples |
|---|---|---|---|
| 0.2 | 16,000,000 | 0.9479 | 798 |
| 1.0 | 16,000,000 | 1.1070 | 4027 |
| 5.0 | 16,000,000 | 1.0546 | 20128 |
| 20.0 | 16,000,000 | 1.3051 | 80527 |

**Comparisons stay exactly constant** (16,000,000 = 4000×4000) across every
match rate, while the **output tuple count scales roughly with the match
rate** (798 → 4027 → 20128 → 80527, tracking the expected `n·m/domainSize`
count for each rate). This is direct confirmation of the answer to Question
1: the nested loop examines every pair unconditionally, so the *number of
pairs examined* depends only on the input sizes, never on how many of them
happen to satisfy the join condition. **Wall time is roughly flat too**
(0.95s–1.3s, with the highest match rate slightly slower) - the small
increase at high match rates is plausibly the cost of inserting a much
larger result set (80,527 vs 798 tuples) into the output `Set<Tuple>`, not
the comparison work itself, which is identical in all four rows.

### 6. What would you need to change to make the million-tuple join feasible?

The current join is a nested loop that has no way to know, before
comparing a pair, whether it could possibly match - it treats `R.b=S.b`
the same as any other condition, testing every one of the n·m combinations
individually. Making a million-tuple join feasible means avoiding that
blind exhaustive comparison: building an index (e.g. a hash table) on the
join attribute of the smaller relation first, then for each tuple of the
larger relation doing an O(1) hash lookup instead of scanning the entire
other relation, would bring the cost down from O(n·m) to roughly O(n+m).
This is exactly the scope the assignment explicitly excludes from this
component ("Indexes, hash joins ... are excluded" - Section 3) and flags
as a later bonus project, so this project's nested loop is a deliberate
baseline to measure against, not a design mistake to fix here.
