# Experiment 1 — Findings

Interpretation of `experiments/results.md`. 62 solutions to 4clojure
problem 22 ("Count a Sequence") scored by (a) the existing v1 Halstead
pipeline and (b) informal implementations of the v2 candidate metrics
from `metrics_research.md`.

> **Related docs.** See [`../review.md`](../review.md) for the v1 audit,
> [`../metrics_research.md`](../metrics_research.md) for the metric
> theory the new metrics are drawn from, and
> [`../artifacts_research.md`](../artifacts_research.md) for the v2
> packaging plan.

## TL;DR

- **All 62 snippets are unique after comment-strip + whitespace-collapse
  SHA dedup.** Even snippets that look near-identical (e.g. ten variants of
  the `reduce (fn [n _] (inc n)) 0` idiom) differ in identifier names or
  punctuation enough to produce distinct SHAs. Verdict: the SHA dedup
  *works* but it dedups only exact rewrites; for "near-duplicate"
  detection we'd need a structural fingerprint (sorted token stream, AST
  shape hash) instead.
- **The v1 Halstead pipeline silently fails on 4 of 62 snippets** (6%).
  Three return `NaN` because the input is a bare expression that the EDN
  reader reads as a single symbol; one is unreadable because of the `@x`
  deref reader macro. The v1 badge would currently show `NaN` or
  `Infinity` on these in the browser.
- **The new metrics produce a clean three-tier ranking** — functional
  reduce-style solutions cluster at 85–100, recursive `if`-tree solutions
  at 84, imperative `loop`/`recur` solutions at 67–77 — that matches
  intuitive human ranking. Halstead's `effort` ranking does not produce
  the same tiers; it spreads the second and third tiers together and
  rewards terseness in ways that don't track clarity.
- **Several rank disagreements between the two scoring systems are real
  signal**, not noise. The biggest disagreements are exactly the cases
  where Halstead penalises a verbose-but-clear refactor or rewards a
  terse-but-clever one.

## Method recap

- 62 source strings stored as a Clojure vector in
  `experiments/4clojure_022_example_solutions.clj`.
- Canonical form = `remove-comments` (the existing v1 implementation) →
  collapse whitespace → trim. SHA-1 of that, truncated to 12 hex chars.
- v1 Halstead = `text-input-to-short-metrics` → `tokens-to-metrics-map`.
  Wrapped in `try/catch`. Detected NaN after the fact.
- v2 candidate metrics computed on the form read with the same hacky
  preprocessing the v1 code does (`replace-hash-parens`,
  `replace-esc-regex-hashes`, `remove-slashes`) so we can compare on the
  same parser footing:
  - **Cognitive Complexity (CC\*)** — simplified Campbell rules:
    `+1` for each `if`/`when`/`when-not`/`if-not`/`cond`/`condp`/`case`/`loop`/`try`,
    plus the current nesting level. `let` and threading macros do NOT
    increment.
  - **Nesting depth** — max depth counting only scope/branch-introducing
    forms (`let`/`loop`/`fn`/`if`/`when`/`cond`/`case`/`for`/`doseq`/`try`).
    Threading macros do NOT count.
  - **`let`-binding count** — names introduced by
    `let`/`loop`/`letfn`/`if-let`/`when-let`.
  - **Anonymous-fn count** — `fn`/`fn*` occurrences (`#()` reads as `fn*`).
  - **Side-effect markers** — `!`-suffixed symbols + a known list
    (`println`, `slurp`, `ref`, `dosync`, `alter`, `set!`, etc.).
  - **Composite clarity score** — weighted combination per
    `metrics_research.md` §5 with identifier-quality and doc-readability
    omitted (out of scope for informal). Weights renormalised:
    CC 0.36, depth 0.29, scope-load 0.21, side-effects 0.14.

## Notable surprises

### 1. Bare expression snippets break v1 silently

```clojure
reduce #(+ %1 (if %2 1 1)) 0
reduce (fn [x y] (+ x 1))  0
reduce #(if %2 (inc %1)) 0
```

These are bare expressions (three top-level forms each, not wrapped in
`fn`/`#()`). 4clojure does text substitution: dropping
`reduce #(if %2 (inc %1)) 0` into `(= (__ '(1 2 3 3 1)) 5)` yields
`(= (reduce #(if %2 (inc %1)) 0 '(1 2 3 3 1)) 5)` — a valid 3-arg `reduce`
call that evaluates to 5. So the submissions are real; they just aren't
*expressions that reduce to a function*. The v1 pipeline doesn't do text
substitution — it calls `cljs.reader/read-string` directly, which reads
**one form** and stops. It reads `reduce`, gets the symbol back, computes
Halstead on `{:operators [reduce] :operands []}`, divides by zero,
returns NaN. **The v1 badge shows `V:NaN D:NaN E:NaN` on these snippets
in production.**

The v2 candidate metrics get the same form, so they also report
`CC=0 depth=0`. The composite clarity score gives them 100/100 because
"a single symbol" is trivially clear. **This is a failure mode of both
old and new metrics** that the v2 implementation must address: detect
when the input has multiple top-level forms and either score the
combined token stream or reject the input.

### 2. The `(ref ...) (dosync ...) @counter` solution

```clojure
(fn [x]
  (let [counter (ref 0)]
    (loop [s x]
      (when (not= s nil)
        (dosync (alter counter inc))
        (recur (next s))))
    @counter))
```

EDN can't read `@counter` (deref reader macro). The v1 path returns
`ERR(unreadable)`. The new metrics also can't compute on it because the
form never reads. **In production this is a silent failure for both v1
and v2** — the row in the table is the only place the user would know.

For v2, the right answer is `cljs.tools.reader/read-string` which handles
all reader macros. Once it parses, this snippet should get a *very* bad
clarity score because of the `ref`/`dosync`/`alter` side-effect markers
plus the nesting. Both Halstead and clarity would correctly call this
unclear if they could read it. **Switching readers is the single most
important v2 fix.**

### 3. Clarity vs. Halstead — the three meaningful disagreements

**a) Halstead loves terse-recursive; clarity flags it.**

```clojure
(fn cnt [xs] (if xs (+ 1 (cnt (next xs))) 0))
```

Halstead `V/D/E = 34.87 / 3.75 / 130.76` (rank #22 by effort).
Clarity `84` (rank #37). CC=1 (single `if`), depth=2, anon-fns=1.

Halstead rewards the terseness. Clarity says: yes, it's short, but it's
recursive over `next`, the recursion is hidden by the absence of
`recur`, and the truthy-check `(if xs ...)` is *not* the same as
`(if (seq xs) ...)` (it diverges on lazy seqs vs persistent vectors).
This is a "clever, short, easy to misread" solution. **Clarity is
right.**

**b) Halstead loves the `if (= x [])` recursive style; clarity says
"this is the worst recursive form."**

```clojure
(fn lala [x] (if (= x []) (+ 0 0) (+ 1 (lala (rest x))) ))
```

Halstead `V/D/E = 46.51 / 5.25 / 244.18` (rank #34).
Clarity `84` (rank #22 — clearer than Halstead says).

`(+ 0 0)` instead of `0` is bizarre. `(= x [])` instead of `(empty? x)`
is non-idiomatic. But the *structure* is identical to the other
recursive solutions. Clarity says "structurally fine, naming aside";
Halstead penalises the heavier token count. **Halstead is technically
more discriminating here, but only because of cosmetic token weight.
This is the case where Halstead "gets it right by accident."**

**c) Halstead and clarity disagree on the imperative loop solutions.**

The `loop`/`recur` variants cluster into three sub-tiers by clarity:

- **9 snippets at clarity=74** — `#()`-wrapped, `(if ...)`,
  CC=3, depth=2, lets=2, anon-fns=0.
- **2 snippets at clarity=72** — `(fn [...])`-wrapped (so anon-fns=1),
  using `if-not`. CC=3, depth=2, lets=2.
- **6 snippets at clarity=67** — extra nesting (the `loop` is inside an
  outer `fn` body that's not the immediate wrapper), CC=3, depth=3.

Halstead `V` ranges 46–63 across these, `E` ranges 363–706 — giving
seventeen different effort numbers for what is essentially three
classes of solution. **Clarity correctly bins related solutions
together; Halstead implies seventeen levels of clarity that aren't
really there.** The right answer is: these are the same idiom modulo
nesting depth, score by tier, then *rank within a tier by naming
quality* — which neither metric can do without identifier-quality
detection (not implemented in this experiment).

### 4. `#(.size (vec %) )` got clarity 100 and that's wrong

```clojure
#(.size (vec %) )
```

Clarity score: 100. CC=0, depth=0, anon-fns=0 (because `#()` expands to
`fn*` which my walker doesn't catch when the form is read as a
single-shot `(.size (vec %))` after `replace-hash-parens` strips the `#`).
Halstead `V/D/E = 4.75 / 1 / 4.75`.

**Real verdict:** this calls Java/JS interop on a polymorphic argument
that may or may not have a `.size` method (works on `vec` because vectors
expose it on the JS/JVM, but the *idea* of dispatching to host interop
in user code is exactly what 4clojure problem 22 is testing you against
— the canonical solution is `count`, which is the explicit restriction).

Both metrics miss this. Halstead misses it because it's short. Clarity
misses it because:
(a) the `#()` reader macro strips and loses the `fn` operator;
(b) my metric doesn't detect host-interop dispatch (`.method` calls);
(c) my metric doesn't penalise "uses a banned construct" (problem 22
specifically forbids `count`, so a clarity tool that ranked solutions
would want a rule like "uses host interop" as a *cleverness* flag).

**For v2**: a clarity score in the 4clojure context probably wants a
"cleverness penalty" for host interop, reflection, eval, and string-eval
tricks. None of those are *bad code* in general, but they're bad
*pedagogically* on a "count a sequence" exercise.

## Where the metrics agree, and what that tells us

The strongest agreement: **the top of both rankings is the same idiom**:

```clojure
#(reduce + 0 (map (constantly 1) %))     ; clarity 100, Halstead V 19.65
#(reduce (fn [n e] (+ 1 n)) 0 %)         ; clarity 93,  Halstead V 27
#(apply + (map (fn [x] 1) %))            ; clarity 93,  Halstead V 19.65
```

Both metrics say: a flat `reduce`/`map` chain is the clearest. That's
the right answer and it's good that both metrics agree.

The strongest agreement at the bottom: **explicit `loop`/`recur` with
multiple bindings and nested `if`** is the least clear. Both metrics
put `(fn [x] (loop [col x ct 0] (if (nil? col) ct (recur (next col)
(inc ct)))))` near the bottom. Again, right answer.

The disagreement zone is the *middle* — the simple recursive-if
solutions. Halstead spreads them, clarity bins them. **For the v2
"percentile framing on this page" feature, the binned ranking is
better**: users want to be told "you're in the recursive-if tier",
not "you're at position #37 of 62".

## Side-effect markers: zero hits except the unreadable snippet

Of the 62 snippets, only the `(ref ...) (dosync ...) @counter` solution
contains side-effect markers — and it doesn't parse. So the side-effect
metric contributed **zero discriminating information** in this
experiment.

This is actually a property of problem 22: there's no reason to use side
effects to count a sequence. On a more open-ended problem (e.g.
implementing a memoizing function), the side-effect count would
discriminate more. **Keep the metric; just don't expect it to fire on
every dataset.**

## Methodological flaws of this experiment (and what they imply for v2)

1. **Single-symbol top-level forms fool everything.** Three snippets
   (`reduce ...`) start with a bare symbol; both readers stop after one
   form. v2 must detect this and either score the combined text or
   reject it with a clear error.
2. **`#()` loses `fn` after `replace-hash-parens`.** My anonymous-fn
   counter saw 0 for many `#()`-using snippets. With
   `cljs.tools.reader/read-string`, `#()` reads as `(fn* [...] ...)` and
   the counter would catch it. **`tools.reader` migration is necessary
   not just for correctness but for metric accuracy.**
3. **Reader-macros block analysis silently.** `@counter`, `'()`,
   `#"regex"` all fail at the EDN reader. The v1 code's
   `read-clojure-string` returns `nil`, the `tokens-to-metrics-map`
   divides by zero, the badge shows `NaN`. v2 must validate before
   computing.
4. **No identifier-quality metric was computed.** A real comparison
   between, say, `(fn laenge [cc] ...)` and `(fn count-elements [coll]
   ...)` would shift clarity scores meaningfully. The German `laenge` +
   abbreviated `cc` should cost ~10–15 points relative to
   `count-elements` + `coll`. **For v2, identifier-quality is the
   single biggest gap in the current implementation.**
5. **No doc readability metric.** None of the 62 snippets have a
   docstring (4clojure submissions are bare expressions). On
   clojuredocs examples, this metric *would* fire. **Test it on
   clojuredocs corpus next.**
6. **The composite weights are uncalibrated.** I used the §5 weights
   verbatim, renormalised after dropping IQ and DR. In practice, the
   weights should be tuned against the **golden corpus** the architect
   research doc recommended — `clojure.core` should land in the top
   quartile of any tool that calls itself a clarity scorer. We didn't
   do that here; this is informal.

## What this experiment validated about the v2 plan

- **Cognitive Complexity is the strongest single discriminator.**
  Functional solutions get CC=0, recursive get CC=1, looping get CC=3.
  Clean three-tier structure that lines up with intuition.
- **Scope-aware nesting depth supports CC well.** It catches the
  `loop > if > let` pattern in imperative solutions even though CC
  doesn't penalise `let` directly.
- **Composite scoring works** — none of the individual metrics produces
  the right ranking alone, but their weighted combination does.
- **The "always show the breakdown" rule is essential.** The composite
  number alone (`74` for all the loop solutions) is uninformative; the
  CC=3, depth=2, lets=2 breakdown is what tells the reader *why*.
- **`tools.reader` is mandatory.** 4 snippets (6.5%) produce no Halstead
  numbers at all (3 `NaN` + 1 `unreadable`). On top of that, **every**
  snippet that uses `#()` has its `anon-fns` count understated by 1
  because `replace-hash-parens` strips the `#` before reading — that's
  ~30 of 62 snippets with a measurement bug. Combined, that's a silent
  error rate of well over 10% on a tiny, simple corpus. On a larger
  clojuredocs corpus the rate will be higher.

## What this experiment is *not* a substitute for

- **A golden corpus test.** We didn't grade these snippets by hand
  before running the metrics. We're comparing two automatic scorings
  against each other, not against ground truth.
- **A multi-problem dataset.** Problem 22 is the simplest possible
  4clojure problem. Any structural metric will look good on it. Test
  the same setup on problems 50+ before drawing conclusions.
- **Identifier-quality validation.** The biggest v2 differentiator
  isn't implemented here.

## Suggested next experiments

1. **Add identifier-quality scoring** (length sweet spot + dictionary
   word ratio + style consistency) and re-run. Predict: the German
   `laenge` and abbreviated `cc`/`stl` solutions drop ~10 points.
2. **Switch to `cljs.tools.reader`** and re-run. Predict: the 4
   currently-failing snippets get real numbers, and the `#()` anon-fn
   counts increase across the board.
3. **Run on a clojuredocs corpus** — say 20 `core/reduce` examples and
   20 `core/map` examples. Predict: doc-readability metric fires for
   the first time, and the ranking is messier because clojuredocs
   examples are not all aiming at the same problem.
4. **Run on `clojure.core` itself.** Sample 30 functions from
   `clojure.core`. Predict: most score 80–95 on clarity; outliers
   (`clojure.core/destructure`, `clojure.core/load-data-reader-file`)
   score very low. This is the **credibility test** —  if `clojure.core`
   doesn't dominate the top quartile, the tool's weights are wrong.
5. **Stable-rename test.** Run the same snippet under five different
   identifier-rename refactors (`xs` → `coll` → `items` → `s` → `data`)
   and check that clarity is stable except where identifier-quality
   genuinely improves. **Should be stable for cosmetic renames, should
   move for `s` → `items` (length sweet spot).**

## How to re-run

From the project root:

```sh
npx shadow-cljs compile experiment
node out/experiment.js
```

The runner reads `v2/experiments/4clojure_022_example_solutions.clj`
and writes a fresh markdown report to `v2/experiments/results.md`.
Append snippets to the input vector and re-run. SHA dedup prevents
double-counting if you ever feed in the same canonical source.
