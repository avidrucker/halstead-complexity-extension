# Review: Halstead Complexity Extension

A holistic review of the project at `halstead-complexity-extension`, combining a
senior Clojure code review of the implementation with a technical research pass
on what the project is *trying* to do and how well that goal is served by the
current approach.

> **TL;DR.** The happy path works on small well-formed inputs, but ~10% of
> realistic inputs silently produce `NaN` because the v1 pipeline uses the
> EDN reader for non-EDN source. Manifest V2 will stop loading in current
> Chrome. About 140 of the 325 lines in `analysis.cljs` can be deleted by
> switching to `cljs.tools.reader`. Halstead's `bugs = V/3000` /
> `time = E/18` formulas have weak empirical backing — drop or relabel.
> Skip macros; use `defmulti` + a data map instead.

> **How this doc fits with the others.** This is the v1 audit. See
> [`metrics_research.md`](metrics_research.md) for which metrics to use in
> v2 and why. See [`artifacts_research.md`](artifacts_research.md) for the
> v2 repo layout and surface plan. See
> [`experiments/findings.md`](experiments/findings.md) for empirical
> validation of the metric choices on a 62-snippet corpus.

---

## 1. What this project is and what it claims to do

A Chrome extension (Manifest V2, written in ClojureScript via `shadow-cljs` and
Reagent) that parses Clojure source inside code blocks on two sites —
`clojuredocs.org` and `4clojure.oxal.org` — and overlays a small badge in the
corner of each block showing **Halstead Complexity** numbers: Volume,
Difficulty, Effort, plus derived "estimated coding time" and "estimated bugs".

The core pipeline (`src/hello_world_rf/analysis.cljs:1-325`):

1. Read the text content of a code-block DOM node.
2. Pre-process the string (`replace-hash-parens`, `replace-esc-regex-hashes`,
   `remove-slashes`, `remove-comments`) so that `cljs.reader/read-string` (the
   EDN reader) can parse it.
3. Walk the resulting forms (`parse-tree` / `parse-body` / `parse-special`),
   classifying every token as an **operator** (function/symbol/keyword in head
   position, special form names) or **operand** (literal value, identifier
   used as argument).
4. Compute Halstead n₁, n₂, N₁, N₂, vocab, length, volume, difficulty, effort,
   coding time, bug estimate (`tokens-to-metrics-map`).
5. Inject a coloured `<div>` with `V:### D:### E:###` into the code block
   (`src/hello_world_rf/content_script.cljs:38-62`).

The README also lists future goals: nested-form tests, "bad code" tests, an
on/off toggle, code-fence detection, klipse editor support, hover, sortable
blocks on 4clojure.

---

## 2. How well does it do that? — TL;DR

**Intent is clear and the happy path works on small, well-formed examples.** The
test suite at `test/hello_world_rf/analysis_test.cljs:1-107` exercises ~15
shapes of input and they all pass. As a learning project it has shipped a
functioning slice end-to-end.

**But the implementation diverges from the claim in several ways that the test
suite does not exercise:**

- The reader pipeline silently corrupts anonymous-fn literals (`#()` loses the
  `fn` operator), regex literals, character literals (`\a`), and string escapes
  — because `remove-slashes` strips every backslash and `replace-hash-parens`
  drops the `#`. See `analysis.cljs:131-144`.
- Reader macros (`'x`, `` `x ``, `@x`, `#'x`, `#_form`, `^meta`, namespaced
  keywords with aliases) all fail at `cljs.reader/read-string`, which is the
  EDN reader and was never meant to parse full Clojure source. The exception
  is swallowed silently (`read-clojure-string`, line 146–149) and the badge
  silently becomes `V:NaN D:NaN E:NaN` because downstream code divides by zero.
- The clojuredocs path (`codedocs-code-block-to-short-metrics`) and the
  4clojure path (`text-input-to-short-metrics`) compute **different things for
  the same input** — one parses the block as one form, the other splits it
  into top-level forms with a custom paren balancer. The user sees one badge
  but cannot tell which pipeline produced it.
- `parse-body` and `parse-tree` overlap and disagree: nested vectors are
  treated as a single literal in one path and as a sequence of operands in the
  other; a nested `defn` is not recognised inside `parse-body`; a `let` with
  multiple body forms silently drops everything after the first.

**Even bigger picture:** the underlying *metric* — Halstead — is largely
discredited in modern research as a predictor of bugs or coding time, and the
*presentation* (an absolutely-positioned coloured div covering the corner of
the code block, with raw numbers and no plain-English context) is not
accessible to colour-blind or screen-reader users and gives novice Clojure
learners no actionable signal. See §6 below.

---

## 3. Code review — concrete findings

### 3.1 Dead code

The namespace prefix `hello-world-rf` is leftover from a `re-frame` template.
Confirmed by grep that the following files are loaded by **no** extension
entry point:

- `src/hello_world_rf/core.cljs` — referenced only by the `:app` shadow-cljs
  build, which compiles to `resources/public/js/compiled/` and is not loaded
  by the extension (`shadow-cljs.edn:11-19`).
- `src/hello_world_rf/db.cljs`, `events.cljs`, `subs.cljs`, `views.cljs`,
  `config.cljs` — re-frame scaffolding, not imported by the extension.
- `resources/public/index.html` — only used by the `:app` dev server.
- `karma.conf.js` — tests run via `:node-test` (`shadow-cljs.edn:33-35`); karma
  is not wired up.
- The `re-frame` and `binaryage/devtools` deps (`shadow-cljs.edn:5-6`) and the
  `react` / `react-dom` deps in `package.json:12-13` exist only to support the
  unused `:app` build.

### 3.2 Bugs and latent issues in `analysis.cljs`

| Where | Issue |
|---|---|
| `analysis.cljs:131-132` | `#(+ 5 %)` → `(+ 5 %)`. The `fn` operator is lost. Test on line 34 actually documents this regression. |
| `analysis.cljs:135-136` | `#"re"` → `"re"`. Regex becomes indistinguishable from a string operand. |
| `analysis.cljs:143-144` | `remove-slashes` strips **every** `\` — destroys string escapes (`"\n"`→`"n"`), char literals (`\a`→`a`, an unbound symbol). |
| `analysis.cljs:146-149` | `read-clojure-string` swallows the reader exception with `(catch :default _ nil)`. Caller never checks for `nil` cleanly; downstream divides by zero. |
| `analysis.cljs:114-115` | `(map? node) [nil (flatten (seq node))]` — if a map *value* is a function call, `flatten` splats the call into operands, losing the operator. |
| `analysis.cljs:108-115` | `(set? node) [nil (seq node)]` — same problem for sets containing calls. |
| `analysis.cljs:84-93` | `parse-special` `let`/`loop` branch passes `(first body)` to `parse-body`. A `let` with multiple body forms drops everything after the first. |
| `analysis.cljs:74-82` | `defn` branch hardcodes `[name docstring? params body]` and only one body form. Multi-arity, `:pre`/`:post`, attribute maps, `defn-` all unhandled. |
| `analysis.cljs:55-65` | `parse-let-bindings` treats binding *forms* (`[a b]`, `{:keys [x]}`) as symbols via `(cons sym ...)`. Destructuring is silently mishandled. |
| `analysis.cljs:18-22` vs 114-115 | `is-operator?` says keywords are operators, but the map branch puts them in operands. Inconsistent classifier. |
| `analysis.cljs:157` | `(flatten operands)` is a band-aid: when the walk produces nested lists (which it does for map-values-that-are-calls), `flatten` mixes operators back into operands. |
| `analysis.cljs:208-236` | `tokens-to-metrics-map` and `tokens-to-metrics-tuples` have identical 12-line `let` bodies and differ only in return shape. Pure duplication. |
| `analysis.cljs:241-274` | `remove-comments` uses four atoms and `doseq`. A straight `reduce` over the string with a state map gives the same semantics, no mutation. |
| `analysis.cljs:276-292` | `extract-function-calls` is not string-aware. A `)` inside a string literal will pop the stack and corrupt boundaries. (Lucky save: `remove-comments` runs first and is string-aware, but the two state machines are kept separate.) |
| Everything above | The whole "remove comments → balance parens → read-string per form" pipeline is reinventing `cljs.tools.reader` poorly. About **140 lines** disappear if you use the proper reader. |

### 3.3 Issues in `content_script.cljs`

- **Two near-identical functions** `append-halstead-to-node-for-4clojure`
  (lines 38–49) and `append-halstead-to-node-for-clojuredocs` (51–62) differ
  only in which child to read from and the badge background colour. Should be
  one function parameterised by a `site-config` map.
- **Imperative `set!` chains** for five style properties on the badge — better
  done with a single CSS class injected via the manifest's `content_scripts.css`
  (or even a Shadow DOM root) so the badge is isolated from host-page CSS.
- **Mutates the host page**: lines 48, 61, 77 set `position: relative` on the
  host's code block. This changes the host site's layout in unpredictable
  ways. Avoid.
- **`(if A (if B C))`** in `get-page` (17–22) should be `cond`. The string
  sentinels `"clojuredocs"` / `"4clojure"` / `"other"` should be keywords.
- **`js/setTimeout` of 2000ms** (114–123) is a guess-and-hope for "the DOM is
  ready". The right tool is `DOMContentLoaded` plus a `MutationObserver` for
  client-rendered sites (4clojure is one).
- **Mutate-then-clone-and-remove** flow in `get-code-from-code-examples`
  (96–105) is upside-down. Should be: read text → compute → append, not append
  → clone → remove → read.

### 3.4 Build / config hygiene

- **Manifest V2 (`extension/manifest.json:3`) is end-of-life** in Chrome. New
  installs will not work. Migration to MV3 is small here because the extension
  uses almost no Chrome APIs — just `browser_action` → `action`, and adding
  `"run_at": "document_idle"`.
- `package.json:7` `"watch"` script references build targets `browser-test`
  and `karma-test` that **do not exist** in `shadow-cljs.edn`.
- `package.json:9` `"test"` script runs `node out/node-tests.js`, but
  `shadow-cljs.edn:34` outputs to `out/test/node-tests.js`. Path mismatch.
- `shadow-cljs` pinned at `2.26.2` (late 2023). Current is `2.28.x`.
- `extension/manifest.json:17` has a non-standard `_comments` key. Move to a
  README; future manifest validators may reject it.

### 3.5 Testing gaps

The test suite covers `separate-operators-operands` only, on small well-formed
inputs. Substantial gaps include:

- **No test exercises** `tokens-to-metrics-map` itself — the actual metric
  formulas are not verified against the Wikipedia worked example.
- **No empty / error-input tests** — `(reduce)`, `""`, `";; just a comment"`.
- **Reader-macro inputs**: `'x`, `` `x ``, `@a`, `#'v`, `^{:m 1} x`, `#_x`.
- **Control-flow forms**: `if`, `when`, `cond`, `case`, `do`, `try`/`catch`,
  `if-let`, `when-let`. (Currently fall through to generic-call handling;
  `case` test-value clauses are silently mis-classified.)
- **Threading macros**: `->`, `->>`, `some->`, `cond->`.
- **Destructuring** in `let` / `defn` params.
- **Multi-arity `defn`**, `defn-`, `defmacro`, `defmethod`, `defmulti`,
  `defprotocol`, `defrecord`, `deftype`, `reify`, `proxy`.
- **`fn` with name / multiple bodies**, `letfn`.
- **Namespaced symbols** (`str/replace`) and keywords (`:my.ns/foo`,
  `::aliased/foo`).
- **Nested data with calls**: `#{(f 1)}`, `{:k (f 1)}`, `[(f 1)]`.
- **`remove-comments` directly**: semicolons inside strings, escaped quotes,
  `;;;;` banners, `#_` reader-discard.
- **`extract-function-calls` directly**: unbalanced parens, `)` inside a
  string, top-level forms separated only by whitespace.
- **End-to-end** `text-input-to-short-metrics` and
  `codedocs-code-block-to-short-metrics`.

A reasonable target is ~25–30 new test cases organised into `testing` groups.

---

## 4. Should we refactor? — Yes, substantially

The biggest single win is replacing the string-munging + EDN reader with
`cljs.tools.reader`. This collapses `replace-hash-parens`,
`replace-esc-regex-hashes`, `remove-slashes`, `read-clojure-string`,
`remove-comments` (the reader handles `;` natively), `extract-function-calls`,
`cd-block-str-to-call-strings`, and the two divergent entry points into a
single `read-forms` + `parse-tree` pipeline. **About 140 of the 325 lines** of
`analysis.cljs` disappear, and ~10 latent bugs go with them.

### 4.1 Proposed file layout

```
src/halstead/
  core.cljs              ; thin coordinator: text -> metrics
  reader.cljs            ; cljs.tools.reader wrapper -> seq of forms
  parse.cljs             ; forms -> {:operators ... :operands ...}
                         ;   (rewritten parse-tree + special-form dispatch table)
  metrics.cljs           ; tokens -> Halstead metrics map (single function)
                         ;   with metric-meta as data driving labels/units

src/halstead/extension/
  content_script.cljs    ; site config, badge injection, MutationObserver
  popup.cljs             ; existing minimal popup (port over)
  badge.cljs             ; make-badge, CSS class, DOM helpers

test/halstead/
  parse_test.cljs        ; renamed + expanded
  metrics_test.cljs      ; formula tests against Wikipedia example, edge cases
  reader_test.cljs       ; confirms tools.reader handles #(), 'x, @a, etc.
```

### 4.2 Delete outright

- `src/hello_world_rf/{core,db,events,subs,views,config}.cljs`
- `resources/public/index.html` (and the `:dev-http` block in `shadow-cljs.edn`
  if you don't want a dev server)
- `karma.conf.js`
- `:app` build target in `shadow-cljs.edn`
- `re-frame`, `binaryage/devtools` from `:dependencies`
- The broken `"watch"` script in `package.json`

Keep `react` and `react-dom` in `package.json` — Reagent (still used by
the popup) declares React as a *peer* dependency, so the host project
must provide it.

### 4.3 Keep (and migrate)

- `extension/manifest.json` (migrate to MV3)
- `extension/popup.html`
- `popup.cljs` → `halstead.extension.popup`
- `analysis.cljs` → split into the four namespaces above
- `content_script.cljs` → `halstead.extension.content-script`
- `analysis_test.cljs` → split as above
- `dev/user.cljs`

This is a real refactor, not a polish, but the codebase is small enough
(~500 LoC of production code) that it's a half-day of work.

---

## 5. Should we use macros? — No, almost nowhere

Macros pay off when (a) you need to capture syntax a function can't, (b) you
need compile-time computation, or (c) you're generating real boilerplate that a
data-driven function cannot. None of those apply here.

Candidates considered and rejected:

- **A "`defmetric` macro"** declaring formula + label + units in one place.
  This is data, not syntax. A single `def` of a vector of maps, plus a small
  `reduce` to compute dependencies, is strictly better — you can iterate,
  filter, and generate a docs page from it at runtime, which macroexpansion
  forbids.
- **A DOM-badge DSL macro.** Reagent (already a dependency) handles this; if
  you outgrow Reagent's hiccup→DOM compiler there are well-tested options
  (Hicada, Helix). Writing your own is pure cost.
- **A `defparse` macro** for parse rules. `defmulti` / `defmethod` is the
  macro you wanted; it lets users inspect, override, and remove handlers at
  runtime. A custom macro adds no leverage.
- **A test-fixture macro** that captures source + expected map and emits a
  `deftest` with a pretty-printed diff on failure. Borderline. A
  `(defn check [name src expected] ...)` plus a `deftest` calling `check`
  over a vector of triples gives ~90% of the benefit without macro complexity.

**Recommendation: introduce zero macros. Use `defmulti` for parse dispatch and
a config map for metric metadata.**

---

## 6. The bigger question — is Halstead the right metric, and is `V:### D:### E:###` the right way to show it?

### 6.1 Halstead is largely discredited as a *predictor*

The constants `bugs = V/3000` and `time = E/18` come from Halstead's 1977
*Elements of Software Science*, fitted on small datasets. Modern research has
repeatedly failed to validate them:

- **Shepperd (1988)**, *A critique of cyclomatic complexity as a software
  metric*, and follow-up Shepperd & Ince work, show that most validation
  studies suffered from small samples and weak correlations (r² < 0.4), with
  several Halstead derivations not even dimensionally consistent.
  ([source](https://www.cs.du.edu/~snarayan/sada/teaching/COMP3705/lecture/p1/cycl-1.pdf))
- **Fenton & Pfleeger (1997)**, *Software Metrics: A Rigorous and Practical
  Approach*, dismantle Halstead via measurement theory: predictions are
  tautological (Volume is a function of length, length trivially predicts
  bugs), and the Stroud-number constant (18 mental discriminations/sec) was
  borrowed from unrelated 1960s cognitive science and never validated on
  programming tasks.
- **PeerJ Computer Science (2023)** — decomposed Halstead metrics can be
  competitive in *ML-based* fault prediction, but the V/3000 and E/18 formulas
  themselves are the weak part. ([source](https://peerj.com/articles/cs-1647/))
- Almost every modern static-analysis tool (SonarQube, Teamscale, CodeClimate,
  CodeScene) has dropped Halstead from its default dashboard.

Halstead retains some value as a **relative, intra-codebase signal** — function
A vs function B in the same codebase. Absolute "this snippet has 0.3 estimated
bugs" claims should be removed or labelled "rough estimate from a 1977
heuristic".

### 6.2 Alternatives that fit Clojure / short snippets better

| Metric | Fit for Clojure | Notes |
|---|---|---|
| **Cyclomatic complexity (McCabe)** | OK | Must enumerate branching forms (`if`, `when`, `cond`, `case`, `or`, `and`). Under-reports complexity in idiomatic Clojure because conditionals are often replaced by `map` / `reduce` / multimethods. See [`uncomplexor`](https://github.com/lokori/uncomplexor) for a Clojure implementation. |
| **Cognitive complexity (Campbell / Sonar 2018)** | Good | Penalises *nesting*, rewards flat control flow. Maps well onto deeply-threaded Clojure. *Caveat:* Lavazza et al. (JSS 2022) did **not** find it empirically superior to older measures as a readability predictor. Source: <https://www.sonarsource.com/resources/cognitive-complexity/> |
| **NPath complexity** | Poor on short snippets | Multiplicative on nesting; explodes on higher-order calls it can't see. |
| **Maintainability Index** | Avoid | Widely discredited (van Deursen 2014, Teamscale). Folds Halstead's weak signal into an opaque composite with unjustified thresholds. |
| **Clojure-native structural metrics** | **Best for short snippets** | Cheap, honest: max form-nesting depth, `let`-binding count, distinct identifier count, side-effecting-form count (`!`-suffixed, `swap!`, `reset!`, `def`, `print`), arity. Intuitive to readers. |
| **Information-theoretic / entropy** | Noise dominates signal on 1–10 line code. |

**The candor about snippet size matters.** Halstead V, D, E are unstable below
~20 tokens. For the typical clojuredocs example (1–10 lines), the badge
numbers are dominated by structural overhead and are largely noise.

### 6.3 The display itself isn't accessible

The current badge — `V:234 D:12 E:2809` on a green-or-blue background in the
top-right corner of every code block — has several problems:

- **WCAG 1.4.1 "Use of color"** (Level A): colour is the only signal that
  distinguishes "clojuredocs site" (blue) from "4clojure site" (green). And if
  colour is ever used to encode severity (red = hard), that fails outright.
  ([source](https://www.w3.org/WAI/WCAG21/Understanding/use-of-color.html))
- **WCAG 1.4.3 contrast** (AA, 4.5:1 for normal text) and **1.4.11 non-text
  contrast** (3:1 against host-page background) are not checked. Black on green
  is ~5.1:1; black on blue (`#0000FF`) is ~2.4:1 — fails.
- **Screen-reader hostile.** "V234 D12 E2809" read aloud is meaningless.
  Accessible name should be plain language: `aria-label="Halstead complexity:
  moderate. Difficulty 12, volume 234."`
- **No `prefers-reduced-motion` or `prefers-color-scheme`** awareness. Many
  Clojure readers use dark mode; the hard-coded green/blue will look bad.
- **WCAG 2.2.2 "Pause, Stop, Hide"** — no way for the user to dismiss the
  overlay (the README lists this as a future goal).
- **The badge covers code.** The top-right of a code block is often where the
  most semantically loaded function is — the badge occludes the code it
  annotates. SonarLint, JetBrains inspections, and ESLint all use *gutter*
  glyphs or underlines and reveal detail on hover — they never opaquely cover
  the source.

### 6.4 Better ways to show this information

| Idea | Strength |
|---|---|
| **Gutter / margin annotation** instead of an absolute-positioned overlay | Mirrors SonarLint, JetBrains, GitHub PR annotations. Doesn't cover code. |
| **Tooltip drill-downs** on hover/focus | Reveals "what does Difficulty mean?" in plain English. Use `role="tooltip"` + `aria-describedby`. |
| **Sparkline** of per-form contribution to complexity (Tufte 2006) | Word-sized chart showing *which* sub-expression contributes most. Teaches *why*. |
| **Heat-map fill** of the code background using a perceptually uniform Viridis or Cividis ramp | Visual without occupying real estate. Must still pair with text (WCAG 1.4.1). |
| **Percentile framing** ("simpler than 73% of solutions on this page") | The single highest-value change for 4clojure-style pages where many solutions sit side-by-side. |
| **Plain-English summary** ("about as complex as a typical 5-line `reduce`") | Strongly supported by CS-education research on novice feedback (ITiCSE 2022 working group; Springer ETR&D 2023). |
| **Sortable list / leaderboard** on 4clojure (already in the README's future goals) | Comparative framing >> absolute thresholds for motivation. |
| **Post-submission reveal** rather than always-on overlay | The novice-feedback literature finds timing matters; persistent metrics demotivate. |
| **Global toggle** | WCAG 2.2.2 + simple courtesy on clojuredocs. Already in README's future goals. |

### 6.5 A11y non-negotiables (do these before anything else)

1. Never use colour as the sole signal — pair with text and/or icon.
2. Contrast ≥ 4.5:1 for badge text vs badge background; ≥ 3:1 for badge vs
   host-page background. Test in both light and dark mode via
   `prefers-color-scheme`.
3. Provide a plain-language `aria-label` like *"Halstead complexity moderate,
   difficulty 12"*; hide the raw `V:### D:### E:###` from AT with
   `aria-hidden`.
4. Provide a global toggle (WCAG 2.2.2).
5. Switch palette to **Viridis / Cividis** or a ColorBrewer sequential ramp so
   colour-blind users get information.
6. Honour `prefers-reduced-motion` if any reveal animation is added.

---

## 7. Prioritised recommendations

### P0 — correctness and accessibility (must do)

1. **Migrate to Manifest V3.** Without this the extension will not load for new
   users on current Chrome.
2. **Switch parser to `cljs.tools.reader`.** Deletes ~140 lines, fixes ~10
   latent bugs in `#()`, `'x`, `@x`, `#'v`, `#"re"`, `\a`, `"\n"` handling.
3. **Guard against empty operands / read errors** in `tokens-to-metrics-map`
   so the badge never says `NaN`.
4. **A11y essentials**: pair colour with text, contrast checks, plain-language
   `aria-label`, global toggle.

### P1 — clean up dead weight (should do)

5. **Delete the re-frame scaffolding** (`core.cljs`, `db.cljs`, `events.cljs`,
   `subs.cljs`, `views.cljs`, `config.cljs`), the `:app` build target, the
   unused npm/Clojure deps, `karma.conf.js`, `resources/public/`.
6. **Collapse** `tokens-to-metrics-tuples` and `tokens-to-metrics-map` into one
   function with metric metadata as data.
7. **Parameterise** the two `append-halstead-*` functions; move styles to a
   CSS class injected via the manifest's `content_scripts.css`; use Shadow DOM
   or `all: initial` so the badge is isolated from host-page CSS; stop
   mutating the host node's `position`.
8. **Replace the 2-second `setTimeout`** with `DOMContentLoaded` +
   `MutationObserver`.
9. **Fix** `package.json` `"watch"` and `"test"` script paths.
10. **Expand the test suite** to ~25–30 cases covering empty/error inputs,
    `tokens-to-metrics-map` formulas, threading macros, destructuring,
    multi-arity `defn`, reader macros, sets/maps containing calls,
    namespace-qualified symbols.

### P2 — communicate complexity better (changes the product)

11. **De-emphasise or remove** `bugs = V/3000` and `time = E/18`. The evidence
    base is weak; label as "1977 heuristic" or drop.
12. **Add Cognitive Complexity** as a complementary, more nesting-aware metric.
13. **Add Clojure-native structural metrics**: max nesting depth,
    `let`-binding count, distinct identifier count, side-effecting-form count.
14. **Add percentile framing** on 4clojure ("simpler than X% of solutions on
    this page"). Highest-value UX change for that audience.
15. **Move from a corner overlay to a gutter/edge annotation** that doesn't
    cover code.
16. **Add hover tooltips** with plain-English explanations of each metric.
17. **Per-form sparkline** showing which sub-expression contributes most.
18. **Plain-English summary line** ("about as complex as a typical 5-line
    `reduce`") — supported by the CS-education feedback literature.

### P3 — explicitly do NOT do

- Do **not** introduce macros. Use `defmulti` for parse dispatch and a config
  map for metric metadata. Macros add no leverage in this codebase.
- Do **not** add the Maintainability Index. It's an opaque composite of weak
  signals with unjustified thresholds.

---

## 8. Sources cited

- Shepperd, M. (1988). *A critique of cyclomatic complexity as a software
  metric.* Software Engineering Journal, 3(2), 30–36.
- Fenton, N. & Pfleeger, S. L. (1997). *Software Metrics: A Rigorous and
  Practical Approach* (2nd ed.).
- Campbell, G. A. (2018). *Cognitive Complexity.* SonarSource.
  <https://www.sonarsource.com/resources/cognitive-complexity/>
- Lavazza, L., Abualkishik, A. Z. & Morasca, S. (2022). *An empirical
  evaluation of cognitive complexity as a code understandability metric.*
  Journal of Systems and Software, 197.
- Decomposed Halstead metrics for fault prediction. PeerJ Computer Science,
  2023. <https://peerj.com/articles/cs-1647/>
- van Deursen, A. (2014). *Think Twice Before Using the Maintainability
  Index.* <https://avandeursen.com/2014/08/29/think-twice-before-using-the-maintainability-index/>
- Tufte, E. (2006). *Beautiful Evidence* — sparklines.
- WCAG 2.1 (W3C): 1.4.1 Use of Color, 1.4.3 Contrast, 1.4.11 Non-text
  Contrast, 2.2.2 Pause Stop Hide.
- ColorBrewer 2.0 — colour-blind-safe palettes. <https://colorbrewer2.org>
- Viridis colour map. <https://sjmgarnier.github.io/viridis/>
- Cardell-Oliver, R. *How can software metrics help novice programmers.*
- ITiCSE 2022 working group on formative feedback. ACM DL.
- `uncomplexor` — Clojure cyclomatic complexity tool.
  <https://github.com/lokori/uncomplexor>
