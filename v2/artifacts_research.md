# v2 Architecture & Artifacts — From Halstead Overlay to Clarity Engine

*Senior-architect assessment of what the v2 rewrite should be shaped into,
which artifacts/repos should exist, and what value each drives for Avi
personally, for Dataico, and for the wider Clojure community.*

> **TL;DR.** Split the analyzer from the surfaces. Ship a `.cljc` core
> library (`clarity-clj`) + a refactored Chrome extension v2 as Phase 1
> (~6 weeks). Web app + Babashka CLI in Phase 2. `clj-kondo` hook +
> GitHub Action in Phase 3. Monorepo with multiple `deps.edn` aliases, not
> separate repos. MIT license. Calibrate against `clojure.core` — that's
> the credibility test. Skip Cursive plugin, Slack bot, and academic paper.

> **Related docs.** See [`review.md`](review.md) for the v1 audit and what
> to delete; [`metrics_research.md`](metrics_research.md) for which metrics
> the core library should compute and why;
> [`experiments/findings.md`](experiments/findings.md) for empirical
> evidence that the chosen metrics actually rank Clojure snippets the way
> humans do.

---

## 1. The core insight: separate the engine from the surfaces

**Do it. The engine/surface split is not optional for v2 — it's the central
architectural decision that makes everything else cheap.**

The current repo conflates three things that have no business being
entangled:

1. A **parser/analyzer** that takes a string of Clojure source and produces
   metrics.
2. **DOM scraping** logic specific to clojuredocs.org and 4clojure.oxal.org.
3. The **Chrome extension shell** (manifest, content scripts, message passing).

The metrics-research conclusion already pushes you toward
`cljs.tools.reader`, which is portable to CLJ via `clojure.tools.reader` —
there is essentially zero technical reason the analyzer has to live inside
the extension, and several reasons it shouldn't.

The proposed boundary is sharp and well-typed:

```clojure
(clarity.core/score source-string opts)
  => {:clarity-score 87
      :percentile 0.92
      :metrics {:halstead {...}
                :readability {...}
                :structure {...}}
      :summary "Plain-English one-liner"
      :hints [{:severity :info, :message "..."} ...]}
```

That signature is **`.cljc`**. It has no dependency on `js/document`,
`chrome.*`, re-frame, or anything browser-shaped. Every surface — Chrome
extension, web app, CLI, clj-kondo hook, GitHub Action, Calva command, Slack
bot — becomes a thin adapter: *get a string, call `score`, render the
result in my native idiom.* This is the same shape that made `instaparse`,
`clj-kondo`, `rewrite-clj`, and `cljfmt` succeed: a portable core with
surface-specific wrappers.

### The honest pushback

For a one-person nights-and-weekends project, multi-artifact discipline has
costs:

- Two repos means two README files, two CI pipelines, two release flows.
- If they're separate repos and the core lib's output shape changes, the
  extension breaks until both ship a coordinated release.
- "Pure cljc" is aspirational: the moment you want to slurp a file, hit the
  network for percentile baselines, or cache parsed ASTs, you're back to
  platform-specific code.

**Verdict.** Split anyway — but as a **monorepo with multiple `deps.edn`
aliases**, not as separate Git repos on day one. You get the architectural
discipline (the core lib really can't reach into DOM code because it's in a
different source root with a separate `deps.edn` profile) without the
multi-repo coordination tax. Promote to multi-repo only when (a) the core
lib has external consumers pinning specific versions, or (b) surfaces have
genuinely independent release cadences. For a hobby/portfolio project with
one maintainer, that day may never come — and that's fine.

The portfolio framing actually *reinforces* the split. "I made a Chrome
extension" is a junior-engineer line item. "I designed a portable analysis
library and shipped it through four surfaces including a `clj-kondo` hook"
is a senior-engineer story. **The split is the story.**

---

## 2. Catalog of plausible surfaces, ranked

Effort: S < 1 week, M ~ 1 month, L ~ quarter, XL ~ half-year nights/weekends.
Other columns 1–5.

| Surface | What it is | Who uses it | Effort | Audience | Portfolio | Dataico | Verdict |
|---|---|---|---|---|---|---|---|
| **Core library `clarity-clj`** | `.cljc` dep on Clojars, `(score src)` API | Tool authors, downstream surfaces | M | 5 | 5 | 4 | **Ship first** |
| **Chrome extension v2** | Existing surface, refactored on top of core | 4clojure/clojuredocs learners | M | 4 | 3 | 1 | **Ship first** |
| **Babashka script** | `bb clarity.clj foo.clj` — single-file analysis | Clojurists, CI scripts | S | 3 | 3 | 3 | **Ship first or second** (nearly free once core exists) |
| **CLI tool** (`clj -M:clarity path/to/file.clj`) | deps-tools invocation | Solo devs, pre-commit | S | 3 | 3 | 3 | **Ship second** |
| **clj-kondo hook/config** | Inline lint integration | Every serious Clojure shop | M–L | 5 | 5 | 5 | **Ship third** (highest ecosystem leverage) |
| **GitHub Action** | Annotates PR diffs with clarity deltas | OSS maintainers, reviewers | M | 4 | 4 | 3 | **Ship fourth** |
| **Web app playground** | Paste-code-get-score on GH Pages | Curious passersby, blog readers | S–M | 3 | 4 | 2 | **Ship second** (cheap once core compiles to JS) |
| **Calva extension** | Inline VS Code hints | VS Code Clojurists | L | 4 | 4 | 4 | **Ship later** (~6 months in) |
| **Cursive plugin** | IntelliJ integration | JetBrains Clojurists | XL | 4 | 4 | 5 | **Skip** (Kotlin/Java plugin dev is a different craft) |
| **REPL helper** `(clarity.tap/score ...)` | tap>-friendly wrapper | Anyone at a REPL | S | 2 | 2 | 3 | **Bundle with core lib**, not separate |
| **Slack/Discord bot** | Paste code, bot replies | Clojure communities | M | 2 | 2 | 1 | **Skip** (ops burden, no clear demand) |
| **Blog post / talk** | "Scoring Clojure clarity: what works, what doesn't" | Clojure community | M | 5 | 5 | 3 | **Ship alongside core lib** |
| **Academic-style paper** | Formal write-up of methodology | ~20 people | L | 2 | 3 | 1 | **Skip** unless a conference asks |

### Why the rankings move

- **Babashka script** scores higher than its modest audience suggests
  because the *effort* is genuinely tiny — if the core lib is `.cljc` and
  uses `tools.reader`, a bb-compatible entry point is a 30-line wrapper.
  Free ROI.
- **clj-kondo hook** is the single highest-leverage ecosystem play. Every
  serious Clojure codebase has clj-kondo wired up. A custom hook flagging
  *"this function scored 87/100 — way above your project p95 of 42"* would
  be genuinely novel. But it's effort-heavy because clj-kondo hooks run in
  an SCI sandbox with constraints — you'll either port a subset of the
  analyzer to SCI-compatible code or expose results through a separate bb
  task that clj-kondo consumes.
- **Web app** sounds like a big build but it's mostly the existing CLJS
  code minus the DOM scraping. Once the core lib targets JS, "deploy a
  textarea and a result panel to GitHub Pages" is a weekend.
- **Cursive plugin** is the only one to outright skip. Cursive is
  closed-source IntelliJ-platform Kotlin work; nothing transfers from your
  Clojure skills, and audience overlap with Calva is large.

---

## 3. Recommended initial repo split

### Monorepo, multi-module, on day one

One Git repo. Top-level `deps.edn` with aliases for each surface. Approximate
layout:

```
clarity-clj/
  deps.edn                      ; root; aliases :core, :ext, :web, :bb, :cli, :test
  core/
    deps.edn                    ; pure cljc, depends only on tools.reader
    src/clarity/core.cljc
    src/clarity/metrics/halstead.cljc
    src/clarity/metrics/readability.cljc
    src/clarity/metrics/structure.cljc
    src/clarity/summary.cljc
    test/...
  surfaces/
    chrome-extension/
      shadow-cljs.edn
      src/clarity/ext/...
      manifest.json
    web-app/
      shadow-cljs.edn
      src/clarity/web/...
    bb/
      bb.edn
      src/clarity/bb/main.clj
    cli/
      src/clarity/cli/main.clj
  doc/
    methodology.md
    metrics_research.md         ; the existing research doc
```

Surfaces resolve the core lib via `:local/root "../../core"` during
development. When you cut a Clojars release, surfaces flip to
`{:mvn/version "..."}` for production builds, while local dev keeps
`:local/root` via a `:dev` alias override. Same pattern as `clj-kondo`,
`rewrite-clj`-based tools, and Fulcro's own multi-module shape.

### Naming candidates

`halstead-complexity-extension` and `hello-world-rf` both have to die. The
project is no longer Halstead-only and no longer an extension-only thing.
Candidates, ranked:

1. **`clarity-clj`** — recommended. Descriptive, follows Clojure naming
   convention (`*-clj`), unambiguous on Clojars, googleable. Chrome
   extension becomes `clarity-clj-extension`, web app `clarity-clj-web` —
   predictable taxonomy.
2. **`lucid`** — short, evocative ("code that's easy to see through"). Risk:
   `lucid.*` namespace collisions with older Clojure utility libraries, and
   without qualification doesn't say "Clojure".
3. **`scrutable`** — "inscrutable" inverted. Memorable but probably too
   cute for a resume line.
4. **`readeasy`** / **`read-clj`** — descriptive but bland.
5. **`clj-clarity`** — fine, but `clj-` prefix more typically wraps Java
   things; `*-clj` reads more like "Clojure tool for X."

**Pick `clarity-clj`.** Reserve the Clojars coordinate (e.g.
`io.github.adrucker/clarity-clj`) early. The Chrome Web Store listing can
market as "Clarity for Clojure" without coordinate baggage.

### Versioning

Use [tools.build](https://clojure.org/guides/tools_build) with git-sha-based
versions (`v0.1.123-abc1234`) pre-1.0, then SemVer once the core API
stabilizes. The core lib gets its own version; each surface rides the core
lib's version (simpler). Start with riding — one version number, less to
track.

### License

**MIT.** Three reasons:

- The Clojure ecosystem is split among EPL (Clojure core), Apache 2.0 (most
  new libs), and MIT (most JS-influenced libs). Any is fine.
- MIT has the lowest friction for the Chrome extension and for embedding in
  commercial Clojure tooling (potentially including Dataico's internal
  tools).
- EPL-1.0's copyleft scope is debated — enough of a question mark to drive
  some corporate adoption away.

Add a `NOTICE` file crediting `clojure.tools.reader` (EPL-1.0) and any other
significant deps. That's the only license-paperwork chore.

### Test discipline

`core/test/...` runs under both `clojure -X:test` (CLJ side,
Cognitect test-runner or Kaocha) and `shadow-cljs compile test` (CLJS side).
Every metric implementation gets paired property-based tests with
`test.check`: "score of `(+ 1 1)` is always lower than score of a 40-line
nested cond." Add **golden-file tests with a curated corpus of Clojure
samples at three difficulty bands** — this becomes the empirical backbone of
the scoring methodology and great blog-post material.

Surfaces get **smoke tests only**: given core returns `{:score 42}`, the
overlay renders 42. Don't re-test the analyzer at the surface layer.

### Extension build pipeline

The honest pain point: shadow-cljs's relationship with `:local/root` deps is
occasionally fussy. Two reliable patterns:

1. **`:local/root` in `shadow-cljs.edn` deps** with the core lib at
   `../../core` in the same repo. What most multi-module Fulcro repos do.
   Works on shadow-cljs ≥ 2.20.
2. **Clojars snapshots** for cross-repo cases. Less relevant in the monorepo
   plan.

**Avoid Git submodules** — 2010s solution to a problem the monorepo already
eliminates, causes CI grief.

---

## 4. Value drivers, per audience

### Avi as learner / portfolio holder

The v1 elevator pitch — *"I made a Chrome extension that overlays Halstead
numbers on clojuredocs"* — is a niche curio. The v2 pitch — *"I designed and
shipped `clarity-clj`, a Clojure code-clarity scoring library with four
surfaces (lib, extension, web playground, bb CLI) and a clj-kondo hook"* —
is a credible senior-IC portfolio piece. Marketable skills it demonstrates,
in order of rarity on a Clojure CV:

1. **Library design discipline** — picking a small API and refusing to leak
   platform concerns into it. The single hardest skill to evidence in
   interviews.
2. **Cross-platform `.cljc`** — working CLJ and CLJS code from one source,
   with babashka compatibility.
3. **Ecosystem integration** — a clj-kondo hook signals "I understand
   Clojure tooling from the inside."
4. **Methodology rigor** — golden-corpus testing + a blog post on scoring
   methodology + honest acknowledgment of where the score is unreliable = a
   research-engineer signal.

The Chrome extension and web app are demo surfaces; they're the "look,
here's the thing running" footer of every conversation about the project.

### Avi as Dataico engineer

Honest assessment: most of v2 won't see direct Dataico adoption, and **that's
OK**.

**Plausible adoption paths:**

- The **GitHub Action**, if Dataico uses GitHub (likely), could surface
  clarity-delta comments on PRs. Adoption barrier low — annotation only, not
  gating. Realistic outcome: "the team likes it, leaves it on for small UI
  repos, ignores it on RAD-heavy ones."
- The **REPL helper** (`(clarity.tap/score (slurp ...))`) is something one
  engineer (you) can use during onboarding or when reviewing a thorny
  resolver. Zero adoption cost.
- The **clj-kondo hook** slots into existing CI without anyone having to do
  anything different. Highest adoption potential at zero advocacy effort.

**Adoption barriers in a working fintech codebase:**

- **RAD-generated code** (form attributes, report state machines, generated
  resolvers) will score "poorly" on Halstead because RAD intentionally
  produces dense, declarative configuration. Your tool needs a "RAD-aware"
  mode or it'll cry wolf at half the codebase. Real research item, not a
  hand-wave.
- Mutations and resolvers have idiomatic shapes that fool naive metrics
  ("too many short let-bindings", "too many keyword args"). You'd need a
  Fulcro/RAD calibration corpus.
- Compliance/fintech codebases are conservative about new CI gates.
  Annotation-only is OK; failing the build because a function "is too
  unclear" gets the tool removed within a week.

The honest internal pitch isn't *"this will help Dataico."* It's *"I built a
tool whose v3 *could* help Dataico if we add RAD calibration, and the v2
already helped me catch a clarity regression in [this PR]."* That's a real
win, not a fake one.

### Clojure learners on 4clojure / clojuredocs

Metrics research already established that **percentile framing on the
current page** and **plain-English summaries** are the biggest UX wins.
Priority order for v2 user-visible improvements:

1. **"Your `(reduce ...)` here scores in the 85th percentile of clarity for
   solutions on this problem"** — relative, contextual, actionable. The bare
   Halstead number in v1 is meaningless to a learner.
2. **"Two things make this hard to read: a 4-deep nesting and an
   unconventional name (`fn1`). Consider extracting the inner reduce."** —
   translate metrics into prose with concrete suggestions.
3. **"3 idiomatic alternatives from clojuredocs"** is a lovely future
   feature but XL effort (requires a curated solutions corpus). Skip for
   v2.
4. **A small "?" link to a methodology page** so users understand the score
   is heuristic.

### Clojure code reviewers (anywhere)

Three distinct sub-surfaces:

- **Inline PR comments via GitHub Action** — low-friction, advisory only.
  *"Function `process-order` scored 87 (delta +35 vs main). Consider
  splitting."*
- **clj-kondo lint integration** — surfaces on every save. Strongest fit
  if the hook is well-tuned.
- **Static threshold gates** — explicitly **do not ship**. Gating CI on a
  heuristic score creates adversarial relationships with the tool. Advisory
  > gating, every time.

### Open-source Clojure ecosystem

Potential consumers of the core lib:

- **Calva / Cursive** as a backend for inline hints — but only if the CLJ
  API is stable and metric semantics are well-documented.
- **clj-kondo**: not as a direct dep (zero-dep policy) but as a separate
  hook authors can opt into.
- **rewrite-clj-based tools** (cljfmt, zprint): not direct consumers, but
  shared ecosystem infrastructure value.
- **Course authors / educators** — the most promising consumer segment.
  Anyone teaching Clojure (university courses, bootcamps, books) could use
  clarity scores in feedback rubrics.

---

## 5. Risks and gotchas

**Scope creep is the dominant risk.** The minimum viable v2 is the **core
lib + the refactored Chrome extension**. Everything else in §2 is a
temptation. Write that MVP list on a sticky note and physically look at it
before starting any new surface.

**Maintenance burden** of N repos grows ~N² when changes cross repos.
Monorepo defers this until evidence demands separation.

**The core thesis** — *"Clojure clarity is auto-scorable"* — is partially
true. Halstead and basic readability metrics correlate with clarity but
don't define it. The honest framing of the tool, both internally and in the
README, must be **"heuristic score, useful as a discussion starter, never
authoritative."** Tools that overpromise on this thesis (cyclomatic-complexity
gates, "code quality" metrics that fail builds) have a long history of being
hated. Tools that underpromise (clj-kondo's "this is *probably* a bug") are
loved. Be in the second camp.

**IP / licensing footguns**: low risk for clojuredocs (CC-BY-SA) and
4clojure (community submissions, liberal usage norm). The extension reads
the rendered DOM and produces a number; it doesn't republish. Add a
one-paragraph "this extension processes code visible in your browser
locally; nothing leaves your machine" note in the Web Store description —
reviewers like that.

**Manifest V3** is mandatory. Shadow-cljs `:browser` builds are
MV3-compatible, but verify nothing eval-based is in the dep tree (re-frame
is fine, `cljs.tools.reader` is fine, `cljs.js` self-hosted compilation is
**not** fine for MV3).

**Cultural risk: "your code is unclear" feedback lands badly.** The Clojure
community has strong taste; a tool that says "this Rich Hickey snippet
scores 42/100" will be mocked into oblivion. Mitigations:

- Frame the score as **learner-oriented**, not authoritative.
- **Calibrate against an actual corpus of widely-respected Clojure code**
  (`clojure.core`, Fulcro, `clj-kondo`, well-regarded books' samples). If
  `clojure.core` scores in the 90th percentile of your tool, the tool is
  credible. If it scores in the 30th percentile, your tool is wrong.
- **Never personalize.** "This function scores X" not "your function scores
  X."
- Lean on **plain-English explanations of *why*** so the reader can
  disagree with the diagnosis rather than the bare number.

---

## 6. Recommended v2 plan

### Phase 1 (weeks 1–6, ~30–60 hours): foundations

Ship the **core library** and the **refactored Chrome extension** together.
This is the irreducible v2.

- Set up the monorepo per §3.
- Move all metric implementations into `core/`, port to `.cljc`, swap to
  `clojure.tools.reader`.
- Build a **golden corpus of ~50 hand-rated Clojure samples** (10 each
  from: `clojure.core`, Fulcro, idiomatic 4clojure solutions, unclear
  4clojure solutions, anonymized real-world snippets).
- Tune the metrics so `clojure.core` lands in the top quartile and the
  "unclear" samples land in the bottom quartile. **This is the credibility
  test.**
- Refactor the Chrome extension to MV3, kill re-frame, become a thin
  DOM-adapter on the core lib. Add percentile framing and plain-English
  summaries.
- Publish core lib as a Clojars snapshot. Publish extension to Chrome Web
  Store.

**Success criterion**: a stranger on r/Clojure can install the extension,
see useful numbers on 4clojure, and read the lib README and understand what
it does in 60 seconds.

### Phase 2 (weeks 7–14, ~40–60 hours): leverage

Ship the **web app playground** and the **babashka CLI**.

- Web app: `core/` compiled to JS plus a textarea, deployed to GitHub Pages.
  Two weekends including a `methodology.md` page that doubles as the
  artifact's elevator pitch.
- Babashka CLI: `bb -m clarity.bb file.clj` produces a one-line score plus
  a `--verbose` mode. Wire up as a pre-commit-hook example in the README.
- Write the blog post. **The single best portfolio leverage of any artifact
  in this plan**, and Phase 2 is when the work justifies the post.

**Success criterion**: someone you don't know stars the repo or comments on
the blog post.

### Phase 3 (weeks 15–24, ~50–80 hours): ecosystem

Ship the **clj-kondo hook** and the **GitHub Action**.

- clj-kondo hook: research-heavy. Likely a separate `clarity-clj-kondo`
  artifact and a sample `.clj-kondo/config.edn` snippet.
- GitHub Action: small Docker image (or composite action) running the bb
  CLI on changed files in a PR and posting annotations. ~10 hours once the
  CLI exists.

**Success criterion**: one external project (not yours) installs the
clj-kondo hook or uses the GitHub Action.

### Indefinite future / probably never

- Cursive plugin
- Slack/Discord bot
- Calva extension (downgrade from "ship later" if Phase 3 fatigue hits —
  it always does)
- Academic paper

### Timeline at 5–10 h/week

- **Phase 1**: weeks 1–6  → credible v2 within 2 months
- **Phase 2**: weeks 7–14 → web app + bb + blog by ~3.5 months
- **Phase 3**: weeks 15–24 → clj-kondo + GH Action by ~6 months

Realistic slippage to 9 months — life happens, and the metric-tuning corpus
in Phase 1 will eat more time than predicted.

---

## 7. Architecture diagram

```
                          +---------------------------+
                          |   clarity-clj  (core)     |
                          |   pure cljc, no DOM       |
                          |   deps: tools.reader      |
                          |                           |
                          |   (score src) =>          |
                          |     {:score N             |
                          |      :percentile P        |
                          |      :metrics {...}       |
                          |      :summary "..."       |
                          |      :hints  [...]}       |
                          +-----+--------+--------+---+
                                |        |        |
        +-----------------------+        |        +---------------------+
        |                                |                              |
        v                                v                              v
+---------------+              +---------------+              +-----------------+
| Chrome ext v2 |              |  Web app      |              |  babashka CLI   |
| MV3, shadow   |              |  GH Pages     |              |  bb.edn task    |
| content-script|              |  textarea +   |              |  pre-commit hook|
| renders score |              |  result panel |              |                 |
+-------+-------+              +-------+-------+              +--------+--------+
        |                              |                                |
        v                              v                                v
   clojuredocs.org             curious passersby                  CI scripts,
   4clojure.oxal.org           blog readers                       solo devs


           Phase 3+ surfaces (deferred):

        +------------------+      +-----------------+      +----------------+
        | clj-kondo hook   |      | GitHub Action   |      | Calva ext      |
        | SCI-compatible   |      | Docker / comp.  |      | nREPL middleware|
        | subset of core   |      | wraps bb CLI    |      | calls core CLJ |
        +------------------+      +-----------------+      +----------------+
              ^                          ^                         ^
              |                          |                         |
              +----+ all depend on +-----+----- core via Clojars --+
                   |
                   |
              +----+--------------------+
              |  REPL helper (in core)  |
              |  (clarity.tap/score s)  |
              +-------------------------+


  Dependency direction is one-way: every surface depends on core.
  Core depends on nothing surface-shaped. The arrow never reverses.
  This is the entire architectural commitment.
```

---

## 8. One last thing

The single most valuable line of code you can write in v2 is the
`methodology.md` document that honestly explains **what the score measures,
what it doesn't, and where it's known to be wrong.**

The Clojure community will forgive a heuristic tool that says *"here's how I
work and here's where I fail."* It will not forgive a tool that pretends to
be authoritative. **Ship the honesty alongside the code.**
