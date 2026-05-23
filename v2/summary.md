# v2 — Summary of Research and Experiments

This directory holds all the planning, research, and empirical-validation work
done for the v2 rewrite of the project (from a Halstead overlay to a Clojure
**code-clarity scorer**). It does NOT yet contain v2 source code — only the
docs and experiments that justify the v2 design.

## The journey, in one diagram

```
   review.md ───────► metrics_research.md ──────► artifacts_research.md
   (what's wrong)    (what to measure instead)   (how to ship it)
        │                       │                          │
        └────────► experiments/findings.md ◄───────────────┘
                  (does any of this actually work?)
```

Each doc was written deliberately, in this order, to answer one question.
The cross-references at the top of each `.md` link them together for any
reader landing on a single file.

---

## Files in this directory

### `review.md`

**Why made.** I needed to know what the v1 code does *correctly*, where it
silently fails, what to keep, and what to delete — before committing any
time to a rewrite. A senior Clojure code reviewer agent + a complexity-metrics
researcher agent ran in parallel and their findings are synthesized here.

**Main takeaways.**

- The happy path works on small well-formed inputs, but **~10% of realistic
  inputs silently produce `NaN`** because the v1 pipeline uses
  `cljs.reader/read-string` (the EDN reader) on non-EDN Clojure source.
  Reader macros like `'x`, `@a`, `#'v`, `#_`, `^meta`, and regex literals all
  fail; `#()` is "fixed" by stripping the `#`, which loses the `fn` operator.
- **Manifest V2** is end-of-life in Chrome. The current extension won't load
  for new users.
- **~140 of the 325 lines in `analysis.cljs`** disappear by switching to
  `cljs.tools.reader` — comment removal, paren balancing, the regex/escape
  preprocessing, and two divergent per-site entry points all collapse into a
  single `read-forms` → `parse-tree` pipeline.
- The entire `re-frame` scaffolding (`core`, `db`, `events`, `subs`, `views`,
  `config`) is leftover template code, loaded by nothing in the extension.
  Delete the namespaces, the `:app` build, and the `re-frame` /
  `binaryage/devtools` deps.
- **Halstead `bugs = V/3000` and `time = E/18`** have weak empirical backing
  (Shepperd 1988, Fenton & Pfleeger 1997). Drop them or relabel as "1977
  heuristic, not validated."
- **Skip macros entirely.** Use `defmulti` for parse dispatch and a config
  map for metric metadata; macros add no leverage in this codebase.
- The current corner-overlay badge violates **WCAG 1.4.1 (Use of Color),
  1.4.3 (Contrast), 1.4.11 (Non-text Contrast), 2.2.2 (Pause Stop Hide)**
  and is screen-reader hostile. Plus it covers the code it annotates.

### `metrics_research.md`

**Why made.** Once `review.md` established that Halstead is the wrong primary
metric, I needed a *defensible* answer for what should replace it — backed by
research, with concrete code examples, and explicitly oriented toward
**reader clarity** (the user's stated goal) rather than performance.

**Main takeaways.**

- Most classical complexity metrics were built for Fortran/Cobol/C and
  validated on full programs. **They have low signal-to-noise on short
  (1–30 line) Clojure snippets.**
- The highest-SNR signals for *reader* clarity, ranked:
  1. **Cognitive Complexity** (Campbell / Sonar 2018) — explicitly designed
     for human comprehension; penalizes nesting.
  2. **Scope-aware nesting depth** — best single-number proxy for working-memory
     load.
  3. **Identifier quality** — length sweet spot + dictionary-word ratio +
     style consistency (Binkley et al. research).
  4. **`let`-binding count + shadowing detection.**
  5. **Side-effect markers** (`!`-suffixed, `swap!`, `reset!`, `def`, IO).
  6. **Docstring readability** via Flesch-Kincaid / ARI applied to docstrings
     when present.
- A worked example on two semantically equivalent snippets shows Halstead
  and Cognitive Complexity *both* rank a recursive-imperative version below
  a flat-functional one — but Halstead does it for the wrong reason (more
  tokens), while Cognitive Complexity does it for the right reason
  (control-flow nesting).
- **Ship a weighted composite of those six metrics, always show the
  breakdown**, never just the headline number. Bands: 80–100 "clear", 60–79
  "ok", 40–59 "consider refactor", < 40 "hard to read."
- Explicit "do not ship" list: LOC alone, Maintainability Index, NCSS,
  Halstead derived bug/time formulas, OO metrics (CBO/DIT/RFC/LCOM), pure
  paren depth, comment-to-code ratio as a continuous score.

### `artifacts_research.md`

**Why made.** Once we know *what* to measure, the question becomes *how to
ship it*. The user explicitly raised the idea of multiple consumers — Chrome
extension, web app, CLI, code-review integration. I asked a senior software
architect agent to decide which surfaces are worth building, in what order,
and as what kind of repo split.

**Main takeaways.**

- **Separate the engine from the surfaces.** Ship a `.cljc` core library
  (`clarity-clj`) with a single `(score src) => {...}` API. Every surface
  (Chrome ext, web app, CLI, `clj-kondo` hook, GitHub Action, REPL helper)
  is a thin adapter. This is the central architectural commitment and the
  thing that turns a Chrome-extension portfolio piece into a senior-IC story.
- **Monorepo with multiple `deps.edn` aliases**, not separate Git repos on
  day one. You get the architectural discipline without the coordination
  tax. Promote to multi-repo only when external consumers force it.
- **Recommended name**: `clarity-clj`. Reserve the Clojars coordinate early.
- **License**: MIT (lowest friction; Reagent/shadow-cljs already permissive).
- **Three-phase plan**:
  - **Phase 1** (weeks 1–6): core library + refactored Chrome extension v2.
    Includes the **credibility test** — calibrate metrics so `clojure.core`
    lands in the top quartile of a 50-snippet golden corpus.
  - **Phase 2** (weeks 7–14): web app playground + Babashka CLI + blog post.
  - **Phase 3** (weeks 15–24): `clj-kondo` hook + GitHub Action.
- **Skip indefinitely**: Cursive plugin (different craft), Slack/Discord bot
  (ops burden, no demand), academic paper.
- **Cultural risk**: a tool that says "this Rich Hickey snippet scores
  42/100" gets mocked. Frame the score as **learner-oriented and advisory**.
  Never gate CI. Always show the breakdown. Ship `methodology.md` alongside
  the code as the most-valuable single artifact in the whole plan.

### `experiments/findings.md`

**Why made.** The previous three docs are research. Before committing weeks
of nights-and-weekends to a rewrite, I wanted **at least one empirical data
point** showing that the proposed metrics actually rank Clojure code the way
humans would. So I built a small harness that runs both the v1 Halstead
pipeline AND informal implementations of the v2 candidate metrics on 62 real
4clojure solutions, deduped by SHA-1 of the comment-stripped source.

**Main takeaways.**

- **The v1 pipeline silently fails on 4/62 snippets (6.5%)** — three
  `NaN` (bare expressions the EDN reader stops on after one form) and one
  `unreadable` (a `@counter` deref reader macro EDN can't parse). **Switching
  to `cljs.tools.reader` is mandatory, not optional.**
- **All 62 snippets are unique by SHA after comment-strip + whitespace
  collapse.** Even near-duplicates differ by punctuation or identifier names
  enough that SHA-based dedup doesn't collapse them. For real near-duplicate
  detection a structural fingerprint (AST shape hash) would be needed.
- **The v2 metrics produce a clean three-tier ranking** that matches
  intuition: flat functional `reduce`/`map` solutions at 85–100, recursive
  `if`-tree solutions at 84, imperative `loop`/`recur` solutions at 67–77.
  **Halstead spreads the tiers together** and rewards terseness in ways
  that don't track clarity.
- The most informative disagreements are real signal, not noise. The
  `(fn cnt [xs] (if xs ...))` snippet (truthy-check, no `recur`, hidden
  recursion bug on lazy seqs) ranks #22 by Halstead but #37 by clarity.
  Clarity is right.
- **Cognitive Complexity is the strongest single discriminator** —
  functional CC=0, recursive CC=1, looping CC=3. Clean tier structure.
- The composite score on its own (e.g. "74") is uninformative; the
  breakdown (`CC=3, depth=2, lets=2`) tells the user *why*. This validates
  the "always show the breakdown" rule from `metrics_research.md`.
- Caveats: no identifier-quality metric was implemented, so naming-based
  refactors (e.g. `laenge` → `count-elements`) wouldn't move the score yet;
  no doc-readability since no 4clojure snippet has a docstring. Both are
  flagged for the v2 work.

### `experiments/results.md`

**Why made.** Generated by the runner. Source of truth for the 62-snippet
score table — every claim in `findings.md` traces back to a row here.

**Main takeaways.** Not a narrative doc; just the data. Sorted by clarity
score descending. Each row shows clarity / CC / depth / let-bindings /
anon-fns / side-effects / Halstead V-D-E / SHA / source snippet.

### `experiments/runner.cljs`

**Why made.** The harness that produces `results.md`. Calls into the
existing v1 `analysis.cljs` for Halstead numbers (so we can compare apples
to apples with what v1 ships), plus informal implementations of the v2
candidate metrics computed on the same read form.

**Main takeaways.** Roughly 380 lines, organized as: canonicalization +
SHA, v1 Halstead wrapper (try/catch + NaN detection), v2 candidate metric
implementations (nesting depth, Cognitive Complexity, let-binding count,
anonymous-fn count, side-effect markers, distinct-identifier count),
composite clarity score, markdown report builder. Writes the report
directly to `v2/experiments/results.md` so stdout-redirection noise
(source-map warnings, status lines) doesn't pollute the report.

### `experiments/4clojure_022_example_solutions.clj`

**Why made.** The input corpus: 62 real solutions to 4clojure problem 22
("Count a Sequence"). All semantically equivalent, all aiming at the same
target — which is exactly the setup that lets us study *clarity* as the
independent variable.

**Main takeaways.** Plain EDN vector of source strings, plus the problem
prompt as a comment at the top. Append to extend the corpus; rerun the
harness; SHA dedup will catch any accidental duplicates.

---

## What's not here (yet)

- **No v2 source code.** The actual `clarity-clj` library, the refactored
  Chrome extension, and any other surfaces will be added later — likely as
  `v2/src/`, `v2/surfaces/`, etc., following the layout proposed in
  `artifacts_research.md` §3.
- **No golden corpus.** The single most important Phase-1 deliverable per
  the strategic advice (50 hand-rated Clojure samples drawn from
  `clojure.core`, Fulcro, idiomatic 4clojure, "unclear" 4clojure, and real
  anonymized snippets) hasn't been built. That's the **credibility test**:
  `clojure.core` must land in the top quartile of your tool, or your
  weights are wrong.
- **No `methodology.md`.** The architect doc identifies this as the single
  most-valuable artifact in the whole plan. Write it *before* writing v2
  code; it forces commitment to a specific theory of clarity.

---

## How the v1 codebase relates to v2

The v1 source code is still in the project root (`src/hello_world_rf/`,
`extension/`, `shadow-cljs.edn`, `package.json`, etc.) and still functions
as-is. The `:experiment` build target in `shadow-cljs.edn` requires v2 work
to coexist with v1 — the runner pulls `hello-world-rf.analysis` into a
node-target build alongside the new metric implementations. When v2 source
code lands, the v1 code will progressively be deleted per the `review.md`
P1 list. Until then, both coexist.

To re-run the experiment from the project root:

```sh
npx shadow-cljs compile experiment
node out/experiment.js
```

This reads `v2/experiments/4clojure_022_example_solutions.clj` and writes a
fresh report to `v2/experiments/results.md`.
