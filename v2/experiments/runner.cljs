(ns runner
  "Calls the existing v1 Halstead pipeline AND informal implementations of
   the proposed v2 alternate metrics on a corpus of 4clojure problem 22
   solutions. Dedupes by SHA-1 of the comment-stripped, whitespace-collapsed
   source so duplicate/near-duplicate solutions only get scored once.

   Run with (from project root):
     npx shadow-cljs compile experiment && node out/experiment.js
   The report is written to v2/experiments/results.md; the only stdout/stderr
   noise is the two [experiment] status lines on stderr."
  (:require [hello-world-rf.analysis :as a]
            [cljs.reader :as reader]
            [clojure.string :as str]
            ["fs" :as fs]
            ["crypto" :as crypto]))

;; ---------------------------------------------------------------------------
;; Canonicalization + SHA
;; ---------------------------------------------------------------------------

(defn canonicalize
  "Strip comments via the existing remove-comments, then collapse runs of
   whitespace, so trivial reformatting doesn't bust the SHA-based dedup."
  [src]
  (-> src
      a/remove-comments
      (str/replace #"\s+" " ")
      str/trim))

(defn sha1-12 [s]
  (-> (.createHash crypto "sha1")
      (.update s)
      (.digest "hex")
      (.substring 0 12)))

;; ---------------------------------------------------------------------------
;; Existing v1 pipeline (Halstead) — wrap so we never throw
;; ---------------------------------------------------------------------------

(defn v1-halstead
  "Calls the existing pipeline. Returns the metrics map or
   {:error <reason>} if the EDN reader gives up on the source."
  [src]
  (try
    (let [ops (a/separate-operators-operands src)]
      (if (:error ops)
        {:error :unreadable}
        (let [m (a/tokens-to-metrics-map ops)]
          (if (or (js/isNaN (:volume m))
                  (js/isNaN (:difficulty m))
                  (js/isNaN (:effort m)))
            (assoc m :error :nan)
            m))))
    (catch :default e
      {:error (str "throw: " (.-message e))})))

;; ---------------------------------------------------------------------------
;; v2 candidate metrics — informal implementations on the read form
;; ---------------------------------------------------------------------------

;; Read with the same hacks the existing code uses, so we can compute
;; structural metrics. Some snippets will still fail (quote, etc).
(defn safe-read [src]
  (try
    (reader/read-string
      (a/remove-slashes
        (a/replace-esc-regex-hashes
          (a/replace-hash-parens src))))
    (catch :default _ nil)))

(def ^:private scope-introducing
  "Forms that introduce a new scope or branch and should bump nesting
   depth. Threading macros deliberately excluded."
  #{'let 'let* 'loop 'fn 'fn* 'letfn 'when-let 'if-let 'when-some 'if-some
    'if 'when 'when-not 'cond 'condp 'case
    'for 'doseq 'try 'catch})

(def ^:private cog-branching
  "Forms that contribute to Cognitive Complexity (Campbell-style, simplified):
   +1 each, plus the current nesting level when nested inside another such
   form. Threading macros and let do NOT count."
  #{'if 'when 'when-not 'if-not 'cond 'condp 'case 'loop 'try})

(def ^:private side-effect-syms
  #{'println 'print 'pr 'prn 'newline
    'slurp 'spit
    'set! 'def 'defn 'defn-
    'ref 'dosync 'alter 'commute 'ensure
    'swap! 'reset! 'compare-and-set! 'cas!
    'send 'send-off})

(defn- side-effect-sym? [sym]
  (or (contains? side-effect-syms sym)
      (and (symbol? sym) (str/ends-with? (name sym) "!"))))

(defn nesting-depth
  "Max depth of nested scope/branch-introducing forms. Threading macros and
   plain function calls do not add depth."
  [form]
  (letfn [(walk [node depth]
            (cond
              (and (seq? node) (symbol? (first node)))
              (let [head (first node)
                    next-d (if (scope-introducing head) (inc depth) depth)
                    child-depths (map #(walk % next-d) (rest node))]
                (apply max next-d child-depths))

              (sequential? node)
              (apply max depth (map #(walk % depth) node))

              :else depth))]
    (walk form 0)))

(defn cognitive-complexity
  "Campbell-style Cognitive Complexity adapted for Clojure: +1 per branching
   form, plus the current nesting level. let/loop bindings, threading macros,
   and plain function calls do NOT increment."
  [form]
  (letfn [(walk [node nest]
            (cond
              (and (seq? node) (symbol? (first node)))
              (let [head (first node)
                    branching? (contains? cog-branching head)
                    self (if branching? (+ 1 nest) 0)
                    next-nest (if branching? (inc nest) nest)]
                (apply + self (map #(walk % next-nest) (rest node))))

              (sequential? node)
              (apply + (map #(walk % nest) node))

              :else 0))]
    (walk form 0)))

(defn let-bind-count
  "Count of names introduced by let/loop/letfn/if-let/when-let bindings."
  [form]
  (letfn [(walk [node]
            (cond
              (and (seq? node) (symbol? (first node)))
              (let [head (first node)
                    self (if (and (#{'let 'let* 'loop 'letfn
                                     'when-let 'if-let
                                     'when-some 'if-some} head)
                                  (vector? (second node)))
                           (quot (count (second node)) 2)
                           0)]
                (apply + self (map walk (rest node))))

              (sequential? node)
              (apply + (map walk node))

              :else 0))]
    (walk form)))

(defn anon-fn-count
  "Count of `fn`/`fn*` forms — the reader expands `#()` to `fn*`, so this
   captures both syntaxes."
  [form]
  (letfn [(walk [node]
            (cond
              (and (seq? node) (symbol? (first node)))
              (let [self (if (#{'fn 'fn*} (first node)) 1 0)]
                (apply + self (map walk (rest node))))

              (sequential? node)
              (apply + (map walk node))

              :else 0))]
    (walk form)))

(defn side-effect-count
  "Count of side-effect markers: !-suffixed symbols and known mutating fns."
  [form]
  (letfn [(walk [node]
            (cond
              (symbol? node)
              (if (side-effect-sym? node) 1 0)

              (sequential? node)
              (apply + (map walk node))

              :else 0))]
    (walk form)))

(defn distinct-identifier-count
  "Number of distinct symbols used. Cheap proxy for vocabulary."
  [form]
  (let [acc (volatile! #{})]
    (letfn [(walk [node]
              (cond
                (symbol? node) (vswap! acc conj node)
                (sequential? node) (doseq [c node] (walk c))
                :else nil))]
      (walk form)
      (count @acc))))

;; ---------------------------------------------------------------------------
;; Composite clarity score (from metrics_research.md §5)
;; ---------------------------------------------------------------------------

(defn- inv-cap
  "Maps a 'bad' raw count into a 0-100 'good' score: 0 raw -> 100, raw>=cap -> 0."
  [raw cap]
  (max 0 (- 100 (* (/ 100 cap) raw))))

(defn clarity-score
  "Subset of the composite from metrics_research.md §5: omits identifier
   quality and doc readability (requires dictionary + ARI) — those are out of
   scope for an informal experiment. Re-normalizes the remaining weights."
  [{:keys [cc depth scope-load side-effects]}]
  (when (and cc depth)
    (let [cc-s (inv-cap cc 10)
          d-s (inv-cap depth 6)
          l-s (inv-cap scope-load 8)
          se-s (inv-cap side-effects 5)
          ;; weights from §5 minus IQ (0.20) and DR (0.10), renormalized
          w-cc 0.36
          w-d  0.29
          w-l  0.21
          w-se 0.14]
      (Math/round
        (+ (* w-cc cc-s)
           (* w-d  d-s)
           (* w-l  l-s)
           (* w-se se-s))))))

;; ---------------------------------------------------------------------------
;; Per-snippet analyzer
;; ---------------------------------------------------------------------------

(defn analyze [src]
  (let [canon (canonicalize src)
        sha (sha1-12 canon)
        form (safe-read src)
        v1 (v1-halstead src)
        parseable? (some? form)
        cc (when parseable? (cognitive-complexity form))
        depth (when parseable? (nesting-depth form))
        lets (when parseable? (let-bind-count form))
        anon (when parseable? (anon-fn-count form))
        se (when parseable? (side-effect-count form))
        ids (when parseable? (distinct-identifier-count form))
        scope-load (when parseable? (+ lets anon))
        clarity (clarity-score {:cc cc :depth depth
                                :scope-load scope-load
                                :side-effects se})]
    {:sha sha
     :src src
     :canon canon
     :v1 v1
     :parseable? parseable?
     :cc cc
     :depth depth
     :let-bindings lets
     :anon-fns anon
     :side-effects se
     :scope-load scope-load
     :distinct-ids ids
     :clarity clarity}))

;; ---------------------------------------------------------------------------
;; Report
;; ---------------------------------------------------------------------------

(defn- truncate [s n]
  (let [s (str/replace s #"\s+" " ")]
    (if (<= (count s) n) s (str (subs s 0 (- n 1)) "…"))))

(defn- fmt-num [n]
  (cond
    (nil? n) "n/a"
    (number? n) (str n)
    :else (str n)))

(defn- fmt-halstead [v1]
  (cond
    (:error v1)
    (str "ERR(" (name (:error v1)) ")")

    :else
    (str (fmt-num (:volume v1)) " / "
         (fmt-num (:difficulty v1)) " / "
         (fmt-num (:effort v1)))))

(defn ^:private p! [out s]
  (.push out (str s "\n")))

(defn markdown-report
  "Build the report as a single string so we can write it cleanly to a
   file (no stdout-redirection noise like the source-map warning)."
  [results]
  (let [out #js []
        n-total (count results)
        unique-by-sha (->> results (group-by :sha))
        unique (->> unique-by-sha vals (map first))
        n-unique (count unique)
        dup-counts (into {} (for [[sha rs] unique-by-sha] [sha (count rs)]))
        sorted (sort-by (fn [r] [(- (or (:clarity r) -1))
                                 (or (:cc r) 99)]) unique)]

    (p! out "# Experiment 1 — 4clojure problem 22 (Count a Sequence)")
    (p! out "")
    (p! out (str "> Run from project root: `npx shadow-cljs compile experiment && node out/experiment.js`"))
    (p! out (str "> Input: `v2/experiments/4clojure_022_example_solutions.clj` (" n-total " snippets)"))
    (p! out (str "> See `v2/experiments/findings.md` for interpretation."))
    (p! out "")
    (p! out (str "**Total snippets:** " n-total
                 " — **unique by SHA after comment strip:** " n-unique
                 " — **duplicates collapsed:** " (- n-total n-unique)))
    (p! out "")
    (p! out "Halstead columns are `volume / difficulty / effort` from the v1")
    (p! out "pipeline. `ERR(unreadable)` means cljs.reader rejected the EDN")
    (p! out "even after the hacky preprocessing; `ERR(nan)` means it parsed")
    (p! out "to zero operators or operands and the formula produced NaN/Inf.")
    (p! out "")
    (p! out "| Clarity | CC | Depth | Lets | AnonFn | SE | Halstead V/D/E | Dup× | SHA | Source |")
    (p! out "|--------:|---:|------:|-----:|-------:|---:|----------------|-----:|-----|--------|")
    (doseq [r sorted]
      (p! out
        (str "| " (fmt-num (:clarity r))
             " | " (fmt-num (:cc r))
             " | " (fmt-num (:depth r))
             " | " (fmt-num (:let-bindings r))
             " | " (fmt-num (:anon-fns r))
             " | " (fmt-num (:side-effects r))
             " | " (fmt-halstead (:v1 r))
             " | " (get dup-counts (:sha r))
             " | `" (:sha r) "`"
             " | `" (truncate (:canon r) 60) "` |")))
    (p! out "")
    (p! out "## Failure modes of the v1 pipeline")
    (let [errors (filter #(:error (:v1 %)) unique)]
      (p! out (str "- " (count errors) " of " n-unique
                   " unique snippets produce no usable Halstead numbers."))
      (doseq [r errors]
        (p! out (str "  - `" (:sha r) "` ("
                     (name (:error (:v1 r))) "): `"
                     (truncate (:canon r) 70) "`"))))
    (p! out "")
    (p! out "## Rank disagreements")
    (let [clear-rank (->> unique
                          (filter :clarity)
                          (sort-by (juxt :clarity :cc))
                          reverse
                          (map-indexed (fn [i r] [(:sha r) i]))
                          (into {}))
          halstead-rank (->> unique
                             (filter #(and (:v1 %) (not (:error (:v1 %)))))
                             (sort-by (fn [r] [(:effort (:v1 r))
                                               (:volume (:v1 r))]))
                             (map-indexed (fn [i r] [(:sha r) i]))
                             (into {}))
          shared (filter (every-pred clear-rank halstead-rank)
                         (map :sha unique))
          disagreements (->> shared
                             (map (fn [s] [s
                                           (clear-rank s)
                                           (halstead-rank s)]))
                             (filter (fn [[_ a b]] (> (Math/abs (- a b)) 10)))
                             (sort-by (fn [[_ a b]] (- (Math/abs (- a b))))
                                      >))]
      (p! out (str "Largest rank disagreements (clarity rank vs. Halstead "
                   "effort rank, where lower is clearer):"))
      (doseq [[sha c-rank h-rank] (take 8 disagreements)]
        (let [r (first (filter #(= (:sha %) sha) unique))]
          (p! out (str "- `" sha "` clarity=#" c-rank
                       " halstead=#" h-rank ":   `"
                       (truncate (:canon r) 60) "`")))))
    (.join out "")))

;; ---------------------------------------------------------------------------
;; Main
;; ---------------------------------------------------------------------------

(defn -main [& _]
  (let [data-path "v2/experiments/4clojure_022_example_solutions.clj"
        out-path  "v2/experiments/results.md"
        text (.toString (fs/readFileSync data-path))
        snippets (reader/read-string text)
        _ (.error js/console (str "[experiment] read " (count snippets)
                                  " snippets from " data-path))
        results (mapv analyze snippets)
        report (markdown-report results)]
    (fs/writeFileSync out-path report)
    (.error js/console (str "[experiment] wrote report to " out-path))
    (.exit js/process 0)))

(set! *main-cli-fn* -main)
