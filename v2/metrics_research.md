# Code Clarity Metrics for Clojure — A Practical Research Brief

*Research for the v2 rewrite of the `halstead-complexity-extension`. Target
use case: scoring **reader clarity** of 1–30 line Clojure snippets as seen on
clojuredocs.org and 4clojure.oxal.org — i.e. "is this code easy for a
learner / reviewer / maintainer to understand?" — NOT performance or
profiling.*

> **TL;DR.** Halstead, McCabe, NPath, and the Maintainability Index all
> have low signal-to-noise on short Clojure snippets. The highest-SNR
> signals for *reader* clarity are: **Cognitive Complexity** (Campbell/Sonar
> 2018), **scope-aware nesting depth**, **identifier quality** (Binkley
> et al.), **`let`-binding count with shadowing detection**, **side-effect
> markers**, and **docstring readability via Flesch-Kincaid or ARI**.
> Ship a weighted composite of these six and always show the breakdown —
> never the headline number alone.

> **Related docs.** See [`review.md`](review.md) for the v1 audit;
> [`artifacts_research.md`](artifacts_research.md) for the v2 packaging
> plan; [`experiments/findings.md`](experiments/findings.md) for an
> informal empirical run on a 62-snippet corpus.

---

## 1. The signal-to-noise framing

Most code-complexity metrics were built in the 1970s–80s on Fortran, Cobol,
and C, and validated on **thousands of lines** of imperative code. They were
designed to predict defect density or maintenance effort at program scale.
They were never validated on 5-line functions, and many of them are dominated
by structural noise at that scale.

For our goal — "is this snippet easy for a Clojure learner to read?" — a
metric has **high signal-to-noise ratio (SNR)** if and only if:

1. It **ranks human-clear code below human-confusing code**, consistently.
2. It does so on **small inputs** (1–30 lines), where a single `defn`,
   docstring, or unused `let` binding can move the score by 30%.
3. It is **robust to surface variation that doesn't affect comprehension**
   (renaming `xs` to `numbers` should not change a structural metric).
4. It **correlates with what humans actually find confusing in Clojure
   specifically** — deep nesting, anonymous-function soup, opaque names, side
   effects buried inside pure-looking chains.

Many classical metrics fail criterion (2) catastrophically. Halstead Volume
on a 3-line function is dominated by whether you wrote `(+ a b)` or
`(reduce + [a b])` — the operator count doubles, the volume jumps, but no
human is more confused. Cyclomatic complexity scores most idiomatic Clojure
at 1, because branching is hidden inside `cond`, `case`, `filter`, `when`,
etc., which are function/macro calls — so it can't distinguish a 30-line
`cond` ladder from a 1-liner.

The honest answer is that **structural metrics built for imperative languages
have low SNR on short Clojure code**, and the highest-SNR signals for our
goal are:

- **Cognitive Complexity** (Campbell/Sonar 2018) — designed for human
  understandability
- **Logical nesting depth** (scope-aware, not raw paren depth)
- **Identifier quality** (length sweet spot + dictionary-word ratio + style
  consistency)
- **`let`-binding count and shadowing detection**
- **Side-effect markers** (`!`-suffixed, IO, in-place mutation)
- **Docstring readability** (Flesch-Kincaid / ARI applied to docstrings, when
  present)

The rest of this brief makes that case concretely.

---

## 2. Walkthrough: two snippets, every metric

The two snippets below are semantically equivalent. Every Clojure reader I've
shown them to picks A as clearer in under two seconds. A useful metric must
rank A as easier to read than B.

```clojure
;; A — clear, idiomatic Clojure
(defn sum-positives [xs]
  (->> xs (filter pos?) (reduce +)))

;; B — same behavior, imperative-style
(defn sum-positives [xs]
  (loop [remaining xs, acc 0]
    (if (empty? remaining)
      acc
      (let [x (first remaining)]
        (if (pos? x)
          (recur (rest remaining) (+ acc x))
          (recur (rest remaining) acc))))))
```

| Metric | A | B | Ranks A < B? | Verdict |
|---|---|---|---|---|
| LOC | 2 | 8 | yes | Trivially correct, trivially gamed |
| Halstead Volume | ~28 | ~155 | yes | Correct, but mostly because B has more tokens. Coincidental, not principled |
| Halstead Difficulty | ~3.5 | ~9 | yes | Driven by operator count |
| Halstead "bugs" (V/3000) | ~0.009 | ~0.05 | yes | Both effectively zero. The formula is meaningless at this scale |
| Halstead "time" (E/18) | ~5 sec | ~80 sec | yes | Coincidentally directional, absolute numbers absurd |
| Cyclomatic complexity | 1 | 3 | yes | Captures the two `if`s in B. Modest signal |
| NPath | 1 | 4 | yes | Slightly more sensitive than CC |
| Maintainability Index | ~95 | ~75 | yes | Directional, but composed of Halstead+CC+LOC so inherits all weaknesses |
| **Cognitive Complexity (Sonar)** | **0** | **~6** | **yes — strong** | **Penalizes nesting and `loop`/`recur`. Best classical fit** |
| **Max nesting depth (scope-aware)** | **0** | **4** | **yes — strong** | **A's threading is flat (no scope-introducers); B nests `loop > if > let > if`** |
| `let` binding count | 0 | 2 | yes | Direct measure of state to track |
| Threading-chain length | 2 | 0 | A *higher* | **Beware: this metric inverts** (higher = better here) |
| Anonymous-function count | 0 | 0 | tie | No signal |
| Side-effect markers | 0 | 0 | tie | No signal |
| Arity | 1 | 1 | tie | No signal |
| Distinct identifiers | 4 | 9+ | yes | Reasonable proxy |
| Flesch-Kincaid (no docstring) | N/A | N/A | tie | Needs docstrings to fire |
| Identifier readability | identical | identical | tie | A and B share names |
| Buse-Weimer readability | ~0.7 | ~0.3 | yes | Trained on Java; transfers imperfectly |

**Key observation.** Cognitive Complexity, max nesting depth, and `let`-count
are the metrics that **most cleanly** separate A from B on principled grounds
rather than as a side effect of token-count differences. Halstead and the
Maintainability Index get the direction right but only because B has more
tokens — they would also rank a verbose-but-clear refactor as worse than a
terse-but-clever one. **That is the wrong direction for our goal.**

---

## 3. Catalog of metrics, with examples

### 3.1 Classical metrics

#### Halstead Volume / Difficulty / Effort

`Volume = (N1+N2) * log2(n1+n2)`, `Difficulty = (n1/2) * (N2/n2)`,
`Effort = D*V`, where `n1`/`n2` are distinct operators/operands and
`N1`/`N2` are totals.

```clojure
;; High-signal case: more bindings = more to track, V grows correctly
(let [s (+ a b), d (- a b), p (* a b)]
  (/ (* s d) p))

;; Misleading case: B is clearer despite higher Volume (longer names)
(defn id [x] x)                        ; low V
(defn identity-of [value] value)       ; higher V, but at least as clear
```

Halstead has **no notion of name quality**. It penalizes descriptive
identifiers and rewards terse cryptic ones — exactly backward for our goal.

**Verdict:** Low SNR for short snippets. Useful only as a gross size proxy.
Keep computing it for backward-compat; weight it ~0 in any composite.

#### Halstead "bugs" (V/3000) and "time" (E/18)

Single-experiment derivations from 1977, on PL/I and Fortran, on full
programs. **No validated meaning on a 5-line Clojure function.** Reporting
"0.009 bugs" on `(defn id [x] x)` is theater. Shepperd (1988) dismantled this
for full programs; the critique is sharper at snippet scale.

**Verdict:** Do not surface to users. If kept, hide behind a verbose flag and
label "1977 heuristic, not validated".

#### McCabe Cyclomatic Complexity

`1 + (count of branching constructs)`.

```clojure
;; CC = 5 — and yes, harder to read
(defn classify [n]
  (cond
    (neg? n) :negative
    (zero? n) :zero
    (< n 10) :small
    (< n 100) :medium
    :else :large))

;; CC = 1 (no branches!) — but arguably HARDER to read than the cond above
(defn classify [n]
  (->> [[:negative neg?] [:zero zero?]
        [:small #(< % 10)] [:medium #(< % 100)]
        [:large (constantly true)]]
       (some (fn [[label pred]] (when (pred n) label)))))
```

CC counts only syntactic branches. In Clojure, branching is frequently
encoded as *data* (vectors of pred/result pairs, multimethods, dispatch
maps). The data-driven version has CC=1 but is not obviously clearer. **CC
systematically under-counts complexity in idiomatic Clojure.**

**Verdict:** Medium SNR. Useful as one signal among several; not primary.

#### NPath complexity

Multiplies (rather than adds) through nested branches: `if A {if B} else
{if C}` is NPath=4, CC=3.

```clojure
;; CC = 3, NPath = 4 — captures multiplicative nesting
(if a (if b :one :two) (if c :three :four))

;; CC = 3, NPath = 3 — flat
(cond a :one, b :two, c :three)
```

**Verdict:** Slightly better than CC for Clojure because it punishes nesting.
Still misses data-driven dispatch. Medium SNR.

#### Maintainability Index

`MI = 171 - 5.2*ln(V) - 0.23*CC - 16.2*ln(LOC) + 50*sin(sqrt(2.4 * comment_ratio))`

A linear combination of metrics we've already individually critiqued.
Heitlager, Kuipers, Visser (2007) and van Deursen (2014) recommend
abandonment: coefficients fit on a tiny 1990s Pascal/C corpus, components are
not independent (V, CC, and LOC are heavily correlated), thresholds (65, 85)
are folklore, and the score hides which subcomponent drove it.

**Verdict:** Do not ship in v2. If users want a single number, surface a
composite *you* designed and can defend — not MI.

### 3.2 Modern metrics

#### Cognitive Complexity (Campbell / Sonar 2018) — the standout

Explicitly designed to model **human comprehension** rather than
testability. Three rules:

1. **Increment** for each structure that breaks linear flow (loops,
   conditionals, catch).
2. **Increment more for nesting** — a branch inside a branch costs +2,
   inside a branch inside a branch costs +3, etc.
3. **Ignore "shorthand"** structures that don't add cognitive load.

```clojure
;; CogC ≈ 0 — humans agree, very clear
(defn evens [xs] (filter even? xs))

;; CogC ≈ 6 — humans agree, harder
;; loop (+1) → if (+2, nested in loop) → if (+3, nested 2 deep) = 6
(defn evens [xs]
  (loop [in xs, out []]
    (if (empty? in)
      out
      (if (even? (first in))
        (recur (rest in) (conj out (first in)))
        (recur (rest in) out)))))
```

Misleading case — pure data access has no Cognitive Complexity even when the
data shape is hard to hold in your head:

```clojure
;; CogC = 0 but the nested-key access pattern is a smell
(defn extract [m] (get-in m [:a :b :c :d :e :f]))
```

The Lavazza et al. 2022 empirical study (JSS vol. 197) found Cognitive
Complexity correlates with comprehension better than CC or LOC, **but not by
a huge margin** — it's the best of the bunch, not a silver bullet.

**Verdict: highest-SNR classical metric for our goal. Ship it as primary.**

### 3.3 Structural / Clojure-native metrics

#### Max form-nesting depth (scope-aware)

Naive paren-depth is noisy: `(+ (* a b) (* c d))` has depth 3 but is trivial.
A useful metric counts depth in terms of **scope/control-introducing
forms**: `let`, `loop`, `fn`, `if`, `when`, `cond`, `for`, `doseq`, `case`,
`try`. Threading macros don't count as depth.

```clojure
;; Logical depth 0 — flat threading, no scope-introducers count
(->> xs (map inc) (filter even?) (take 5))

;; Logical depth 4 — nested ifs and lets (defn does NOT count)
(defn f [x]
  (if (pos? x)                       ; +1
    (let [y (* x 2)]                 ; +1
      (if (even? y)                  ; +1
        (let [z (inc y)] (* z 3))    ; +1
        y))
    0))
```

**Verdict:** High SNR. The single best 1-number proxy for "how much state
must I hold while reading this?" Easy to compute, language-appropriate.
**Ship.**

#### `let`-binding count + shadowing detection

```clojure
;; 1 binding, no shadowing — fine
(let [n (count xs)] (when (pos? n) n))

;; 5 bindings, one shadowed three times — confusing
(let [x 1
      y 2
      x (+ x y)   ; shadows
      z (* x 2)
      x (- z 1)]  ; shadows again
  x)
```

**Verdict:** Counts alone are OK; **shadowing is high-SNR** — shadowed names
are reliably confusing in code review. Ship the combination.

#### Function arity

```clojure
;; arity 2 — fine
(defn add [a b] (+ a b))

;; arity 7 — positional memory load
(defn make-user [name email age role org dept manager] ...)
```

Style guides converge on 3–4 args before switching to a map.

**Verdict:** Medium-high SNR. Cheap to compute. Ship; also detect
"many-arg, positional" patterns.

#### Side-effecting forms

Count:
- `!`-suffixed functions (`swap!`, `reset!`, `conj!`)
- `def`/`defn` inside another `defn` (intra-fn `def` is a smell)
- `println`, `print`, `pr`, `prn`
- `slurp`, `spit`, `read-file`, `write-file`
- Non-trivial `deref`/`@` of mutable refs

```clojure
;; pure — easy to reason about
(defn process [xs] (map inc xs))

;; impure — track state, harder
(defn process [xs]
  (let [counter (atom 0)]
    (doseq [x xs] (swap! counter inc) (println x))
    @counter))
```

**Verdict:** High SNR for "can I reason locally?" — a strong proxy for
clarity in Clojure culture. Ship.

#### Threading-macro length

```clojure
;; chain of 3 — easy
(->> xs (map inc) (filter even?))

;; chain of 9 — should be broken up with named intermediates
(->> data (filter :active) (map :orders) (apply concat)
     (filter (comp pos? :total)) (group-by :customer-id)
     (map (fn [[k v]] [k (reduce + (map :total v))]))
     (sort-by second >) (take 10))
```

**Verdict:** Medium SNR. Flag chains > 5–6 steps; don't penalize 3-step
chains.

#### Anonymous function nesting

`#()` can't nest (the reader disallows it), but `fn` inside `#()` and vice
versa create real confusion:

```clojure
;; one anon fn, simple — fine
(map #(* % 2) xs)

;; nested anonymous fns with %1/%2 — which % is which?
(map #(map #(+ % %2) %) matrix vector)
```

**Verdict:** Medium-high SNR for the cases that exist. Easy detection. Ship.

#### Distinct identifier count

Roughly aligned with Halstead `n2` but cleaner because it ignores frequency.

**Verdict:** Medium SNR. Useful as a tiebreaker, weak alone.

### 3.4 Identifier quality (Binkley et al. research)

Foundational papers:

- Binkley, Davis, Lawrie, Morrell (2009). "To CamelCase or Under_score." ICPC.
- Lawrie, Morrell, Feild, Binkley (2007). "Effective identifier names for
  comprehension and memory." ISSE 3(4).
- Binkley, Hearn, Lawrie (2013). "The impact of identifier style on effort and
  comprehension." Empirical Software Engineering.

Findings: **full-word identifiers beat abbreviations**, **very short (1–2
char) and very long (20+ char) names hurt**, **style consistency matters**.

#### Identifier length

```clojure
;; too short — cryptic
(defn f [x y] (* x (+ y 1)))

;; just right
(defn scale-up [factor offset] (* factor (+ offset 1)))

;; too long — verbose-fatigue
(defn calculate-the-scaled-up-value-from-factor-and-offset [factor offset] ...)
```

Sweet spot empirically: **8–20 characters** for most identifiers. Single
letters acceptable for very-local vars (`i`, `x`, `n`) and conventional
parameters (`xs` sequence, `m` map, `k`/`v` key/value).

#### Dictionary-word ratio

Split on `-`/`_`/camelCase, check tokens against a dictionary.

```clojure
;; ratio 1.0 — all real words
(defn calculate-tax [subtotal rate] ...)

;; ratio ~0.3 — abbreviations everywhere
(defn calc-tx [stl rt] ...)
```

**Verdict:** High SNR. Ship.

#### Abbreviation density

Detect known abbreviations (`calc`, `idx`, `tmp`, `ctx`, `cfg`, `req`, `res`,
`usr`) and count density. Maintain a Clojure-idiomatic allowlist (`fn`,
`defn`, `ns`, `req` for ring requests).

#### Style consistency

Clojure convention is `kebab-case`. Detect `camelCase`/`snake_case` in
user-defined names as style violations.

```clojure
;; idiomatic
(defn user-orders [user-id] ...)

;; non-idiomatic — visual friction
(defn userOrders [userId] ...)
```

**Verdict:** Easy implementation, high SNR for "does this look like
Clojure?" Ship.

### 3.5 Natural-language readability scores (the explicit ask)

Apply prose-readability formulas to docstrings, comments, and split
identifiers.

#### Flesch Reading Ease (FRE)

`206.835 − 1.015·(words/sentences) − 84.6·(syllables/words)`.
Higher = easier; 60–70 is "plain English", < 30 is "very difficult".

```clojure
;; FRE ≈ 75 — easy
(defn sum-positives "Adds up the positive numbers in xs." [xs] ...)

;; FRE ≈ 30 — much harder, denser prose for the same function
(defn sum-positives
  "Computes the additive aggregation of the strictly-positive subset
   of the supplied numerical collection via lazy filtration."
  [xs] ...)
```

#### Flesch-Kincaid Grade Level (FKGL)

`0.39·(words/sentences) + 11.8·(syllables/words) − 15.59`. US grade
output. Clojure docstrings should target grade 8–10. Anything above 14 on a
docstring is a smell.

#### Gunning Fog, SMOG, ARI

- **Gunning Fog** counts "complex words" (3+ syllables). Useful when
  docstrings are heavy on "instantiation", "transformation", etc.
- **SMOG**: `1.043 * sqrt(complex_words * 30/sentences) + 3.129`. Conservative;
  designed for ~30-sentence samples but stable on shorter.
- **ARI**: `4.71·(chars/words) + 0.5·(words/sentences) − 21.43`. Uses
  character counts, not syllables — **most robust on technical text** because
  syllable counters mis-handle terms like "atom", "deref", "transducer".

**My recommendation:** for Clojure docstrings, **ARI is most honest** because
it doesn't rely on a syllable counter that will mis-handle Clojure-specific
jargon. FKGL is most familiar to users. **Ship both and let the renderer
pick.**

#### Comment-to-code ratio

```clojure
;; ratio 0 — no help to the reader
(defn h [n] (reduce + (map #(/ 1 %) (range 1 (inc n)))))

;; ratio ~0.7 — clearly explained
(defn harmonic
  "Computes the nth harmonic number: 1 + 1/2 + 1/3 + ... + 1/n."
  [n]
  (reduce + (map #(/ 1 %) (range 1 (inc n)))))
```

**Verdict:** Use **presence** as binary signal ("has docstring?"); avoid the
continuous ratio. The Maintainability Index's `sin(sqrt(2.4·ratio))` term is
unjustifiable. High SNR for presence, low for ratio.

#### "Identifier readability"

Split identifiers on `-`/camelCase, run FRE on the result. Experimental, not
validated for Clojure. In practice, **dictionary-word ratio + average token
length captures the same signal more reliably** — use that instead of
running FRE on identifiers.

#### Caveats on readability scores

Readability formulas were **built for prose**. They make assumptions that
fail on technical text:

- Sentence boundary detection breaks on code embedded in docstrings.
- Syllable counters mis-estimate technical jargon.
- They reward short sentences, but docstrings are sometimes intentionally
  terse.
- They don't capture *clarity of explanation*, just *surface ease*.

Use them as **one input among several**, not as a verdict. See Klare (1974)
"Assessing readability"; DuBay (2004) "The principles of readability"; and
for software-specific caveats, Scalabrino et al. 2018/2019 explicitly discuss
why prose readability metrics transfer imperfectly to code artifacts.

### 3.6 Composite / experimental metrics

#### Buse & Weimer 2010 — the seminal paper

"Learning a Metric for Code Readability," IEEE TSE 36(4).

1. Collected human readability judgments on 100 Java snippets from 120
   students.
2. Extracted ~25 features per snippet (line length, identifier length,
   identifier count, comments, indentation, keywords).
3. Trained a Bayesian classifier on (features → "readable/unreadable") votes.

The model outperformed any individual metric for matching human judgment.
**Their feature list is the best starting point for "what to measure".** Most
of what we recommend below appears in their feature set. Caveat: trained on
Java; some features (indentation, `{` density) don't transfer.

#### Posnett, Hindle, Devanbu 2011 — the simpler model

"A Simpler Model of Software Readability," MSR 2011. A 3-feature logistic
regression (volume, entropy of identifier lengths, lines) recovers most of
Buse-Weimer's predictive power. **Takeaway: simple models work nearly as well
as complex ones.** Argues for shipping a small, defensible composite over an
ML model.

#### Scalabrino et al. 2018/2019 — adding textual features

Adds *textual features* (identifier semantics, Flesch-Kincaid on comments,
term entropy) to Buse-Weimer's structural features. Textual features add
**modest but real** predictive power. **This is direct empirical support for
the user's ask to include FK on docstrings.**

#### Should we train a Clojure-specific ML model?

Probably yes, but not in v2. Posnett's result suggests 3–6 hand-picked
features get most of the way. Training Clojure-specific would require a
labeled corpus of Clojure snippets with human readability scores — which
doesn't exist; you'd have to crowd-source it. **Ship a defensible hand-tuned
composite first; consider ML for v3.**

---

## 4. Signal-to-noise ranking

For scoring clarity on 1–30 line Clojure snippets, ranked best to worst:

| Rank | Metric | SNR | Justification |
|---|---|---|---|
| 1 | **Cognitive Complexity** (Campbell-style, Clojure-adapted) | High | Designed for human comprehension; cleanly penalizes nesting |
| 2 | **Max form-nesting depth** (scope-aware) | High | Best 1-number proxy for working-memory load |
| 3 | **Identifier quality composite** (length sweet spot + dict-word ratio + style) | High | Directly measures what readers notice first |
| 4 | **`let`-bindings + shadowing detection** | High | Shadowing is reliably confusing |
| 5 | **Side-effect markers** | High | Strong proxy for "can I reason locally?" |
| 6 | **Docstring presence + FK/ARI on docstring** | High when present | Direct readability signal where present |
| 7 | **Function arity** | Medium-High | High arity reliably a smell, cheap to detect |
| 8 | **Anonymous-function nesting** | Medium-High | Catches a specific high-confusion pattern |
| 9 | **Threading-chain length** | Medium | Useful past 5–6 steps, noisy below |
| 10 | **Cyclomatic complexity** | Medium | Misses data-driven dispatch but catches `cond` ladders |
| 11 | **NPath** | Medium | Slightly better than CC for nested branching |
| 12 | **Distinct identifier count** | Medium | Reasonable size proxy, weak alone |
| 13 | **Comment-to-code ratio** | Low-Medium | Presence > ratio; ratio is noisy |
| 14 | **Halstead Volume** | Low | Dominated by token count; weakly correlates with comprehension |
| 15 | **Halstead Difficulty / Effort** | Low | Same critique plus nonsensical absolute scale |
| 16 | **Maintainability Index** | Low | Linear combination of weak metrics; obscures source |
| 17 | **Halstead "estimated bugs" / "time"** | Effectively None | Pseudoscientific on short snippets |
| 18 | **LOC alone** | Effectively None | Trivially gameable |

---

## 5. A recommended composite "clarity score" for v2

**Opinion.** Ship a composite of **six metrics**, surface each subscore
individually, and combine with capped weights into a single 0–100 "clarity"
number. **Always show the breakdown** — never just the headline number.

### 5.1 The six chosen metrics

1. **Cognitive Complexity (CC\*)** — adapted Campbell rules for Clojure.
   Increment on `if`, `when`, `cond` branches, `case`, `loop`, `try/catch`.
   Nesting amplifier as in Campbell. Threading macros count as flat.
2. **Logical nesting depth (D)** — max depth counting only
   scope/control-introducing forms: `let`, `loop`, `fn`, `if`, `when`,
   `cond`, `for`, `doseq`, `case`, `try`.
3. **Identifier quality (IQ)** — average across user-defined names of
   `f(length) · dictionary_word_ratio · style_consistency`, where `f(length)`
   peaks at 8–16 chars and falls off both ways.
4. **Local-scope load (L)** — `(# let bindings) + 3 · (# shadowed names) +
   (# anonymous fns)`. Captures working-memory load.
5. **Side-effect markers (SE)** — count of `!`-suffixed forms, intra-fn
   `def`, IO calls.
6. **Documentation readability (DR)** — if docstring present,
   `100 − ARI_grade · 5`, clamped to [0,100]. If absent and function is
   non-trivial (CC\* ≥ 3 or D ≥ 3), penalize. Trivial functions (`identity`,
   one-liners) get a free pass.

### 5.2 Combination

For each subscore, compute a normalized 0–100 "good" score (higher = clearer).
For CC\*, D, L, SE: invert and cap. For IQ, DR: already 0–100.

```
clarity = round(
  0.25 * cc_score
  + 0.20 * depth_score
  + 0.20 * identifier_score
  + 0.15 * scope_score
  + 0.10 * effect_score
  + 0.10 * doc_score)
```

Weights reflect the SNR ranking. Cognitive Complexity and depth dominate
because they have the cleanest research backing. Documentation is last
because it's often absent in idiomatic short Clojure (the language is so
terse that good naming substitutes).

**Bands:** 80–100 *clear*, 60–79 *ok*, 40–59 *consider refactor*, < 40
*hard to read*.

### 5.3 Worked example — snippets A and B

> The exact normalization curves (how a raw value maps to a 0–100 score)
> need to be calibrated against the golden corpus. The numbers below
> are *illustrative* — they show the rough shape of the breakdown, not
> precise output. The directional verdict (A clearer than B) is what
> matters here.

| Subscore | A raw | A → 0–100 (illustrative) | B raw | B → 0–100 (illustrative) |
|---|---|---|---|---|
| Cognitive Complexity | 0 | 100 | 6 | 40 |
| Logical depth | 0 | 100 | 4 | 33 |
| Identifier quality | high | 85 | identical | 85 |
| Local-scope load (let-bindings + 3·shadowed + anon-fns) | 0 | 100 | 3 | 63 |
| Side-effect markers | 0 | 100 | 0 | 100 |
| Doc readability (trivial → free pass; non-trivial+absent → penalty) | 80 | 80 | 30 | 30 |

```
A = 0.25·100 + 0.20·100 + 0.20·85 + 0.15·100 + 0.10·100 + 0.10·80
  = 25 + 20 + 17 + 15 + 10 + 8 = 95   → "clear"

B = 0.25·40  + 0.20·33  + 0.20·85 + 0.15·63  + 0.10·100 + 0.10·30
  = 10 + 6.6 + 17 + 9.45 + 10 + 3 = 56  → "consider refactor"
```

A scores 95, B scores 56. Matches the human verdict (A is much clearer),
and the breakdown columns tell the user *why* B drops: high CogC, deep
nesting, three local bindings to track, no docstring on a non-trivial
function.

---

## 6. Implementation notes for Clojure

| Metric | Source needed? | Forms enough? | Library suggestion |
|---|---|---|---|
| Cognitive Complexity | Forms | Yes | `tools.reader`; walk forms, increment on control forms with nesting tracking |
| Logical nesting depth | Forms | Yes | Same — track current depth during walk |
| Identifier quality | Forms (names are symbols) | Yes | Split on `-`, check against a packaged wordlist (SCOWL or similar) |
| Local-scope load | Forms | Yes | Walk and detect `let`/`loop` bindings; scope stack for shadowing |
| Side-effect markers | Forms | Yes | Pattern-match symbols ending in `!`, plus a known list |
| Documentation readability | **Source preferred** | Partial — `tools.reader` exposes docstrings on `defn` but strips other inline metadata. Use `rewrite-clj` for full fidelity | `rewrite-clj` for source-preserving zipper; FK/ARI is ~50 lines |
| Comment-to-code ratio | **Source required** | No — `tools.reader` discards comments | `rewrite-clj` |
| Cyclomatic complexity | Forms | Yes | Count `if`, `when`, `cond` clauses, `case` clauses, `and`/`or` extra args |

### 6.1 Clojure-specific pitfalls

- **Analyze pre-expansion forms only.** Running analysis after macroexpansion
  can explode a 1-liner that uses a user-defined macro. `tools.reader/read`
  gives surface syntax.
- **`defn` variants.** `(defn name "doc" [args] body)`,
  `(defn name "doc" {meta} [args] body)`, and multi-arity all coexist. Use a
  `defn` spec (or `clj-kondo`'s parser) to robustly locate the body.
- **Threading macros.** `->` and `->>` create artificial-looking nesting.
  Don't count them as depth-increasing for Cognitive Complexity or
  logical-depth metrics.
- **Anonymous-fn variants.** `#(...)`, `(fn [...])`, `(fn name [...])` — handle
  all three.
- **`clj-kondo` analysis output** —
  `clj-kondo --config '{:output {:analysis true}}'` gives var resolution and
  many things you'd otherwise reinvent. Strongly consider it as the analysis
  front-end. <https://github.com/clj-kondo/clj-kondo/blob/master/analysis/README.md>
- **`rewrite-clj`** — for any metric that needs comments, whitespace, or
  source fidelity. <https://github.com/clj-commons/rewrite-clj>

---

## 7. What is NOT worth measuring

Explicit "do not ship" list:

1. **LOC alone.** Trivially gameable; only useful as a denominator.
2. **Raw token count.** Same critique as Halstead Volume without the
   pretense.
3. **Halstead "estimated bugs" (V/3000).** Pseudoscience on short snippets.
4. **Halstead "estimated time" (E/18).** Same critique. "5.7 seconds to
   implement" is theater.
5. **Maintainability Index.** Composite of metrics we've individually
   critiqued.
6. **Any metric requiring execution.** Profiling, runtime branch coverage,
   allocation. Out of scope and orthogonal to clarity.
7. **NCSS.** Cleaner LOC, same weaknesses.
8. **OO metrics** (CBO, DIT, RFC, LCOM). No Clojure analog.
9. **Pure paren depth.** Use scope-aware nesting instead.
10. **Comment-to-code ratio as continuous score.** Use presence as binary
    signal.
11. **Halstead Difficulty as primary.** Keep computing for backward-compat;
    weight ~0 in any composite.

---

## 8. Bibliography

- Buse, R. P. L. & Weimer, W. R. (2010). "Learning a metric for code
  readability." *IEEE TSE* 36(4), 546–558.
- Posnett, D., Hindle, A. & Devanbu, P. (2011). "A simpler model of software
  readability." *MSR 2011*, 73–82.
- Scalabrino, S., Linares-Vásquez, M., Oliveto, R. & Poshyvanyk, D. (2018).
  "A comprehensive model for code readability." *J. Software: Evolution and
  Process* 30(6).
- Scalabrino, S., Bavota, G., Vendome, C., Linares-Vásquez, M., Poshyvanyk,
  D. & Oliveto, R. (2019). "Automatically assessing code understandability."
  *IEEE TSE*.
- Campbell, G. A. (2018). "Cognitive Complexity: A new way of measuring
  understandability." SonarSource white paper.
  <https://www.sonarsource.com/resources/cognitive-complexity/>
- Lavazza, L., Abualkishik, A. Z. & Morasca, S. (2022). "An empirical
  evaluation of the 'Cognitive Complexity' measure as a predictor of code
  understandability." *J. Systems and Software* 197.
- Binkley, D., Davis, M., Lawrie, D. & Morrell, C. (2009). "To CamelCase or
  Under_score." *ICPC 2009*, 158–167.
- Lawrie, D., Morrell, C., Feild, H. & Binkley, D. (2007). "Effective
  identifier names for comprehension and memory." *ISSE* 3(4).
- Binkley, D., Hearn, M. & Lawrie, D. (2013). "The impact of identifier style
  on effort and comprehension." *Empirical Software Engineering*.
- Shepperd, M. (1988). "A critique of cyclomatic complexity as a software
  metric." *Software Engineering Journal* 3(2), 30–36.
- Heitlager, I., Kuipers, T. & Visser, J. (2007). "A practical model for
  measuring maintainability." *QUATIC 2007*. Van Deursen, A. (2014). "Think
  Twice Before Using the Maintainability Index."
  <https://avandeursen.com/2014/08/29/think-twice-before-using-the-maintainability-index/>
- McCabe, T. J. (1976). "A complexity measure." *IEEE TSE* SE-2(4), 308–320.
- Nejmeh, B. A. (1988). "NPATH: a measure of execution path complexity."
  *CACM* 31(2).
- Oman, P. & Hagemeister, J. (1992). "Metrics for assessing a software
  system's maintainability." *ICSM 1992*.
- Halstead, M. H. (1977). *Elements of Software Science*. Elsevier.
- Klare, G. R. (1974). "Assessing readability." *Reading Research Quarterly*
  10(1).
- DuBay, W. H. (2004). "The principles of readability." Impact Information.
