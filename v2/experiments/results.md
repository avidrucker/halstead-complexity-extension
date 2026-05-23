# Experiment 1 — 4clojure problem 22 (Count a Sequence)

> Run from project root: `npx shadow-cljs compile experiment && node out/experiment.js`
> Input: `v2/experiments/4clojure_022_example_solutions.clj` (62 snippets)
> See `v2/experiments/findings.md` for interpretation.

**Total snippets:** 62 — **unique by SHA after comment strip:** 62 — **duplicates collapsed:** 0

Halstead columns are `volume / difficulty / effort` from the v1
pipeline. `ERR(unreadable)` means cljs.reader rejected the EDN
even after the hacky preprocessing; `ERR(nan)` means it parsed
to zero operators or operands and the formula produced NaN/Inf.

| Clarity | CC | Depth | Lets | AnonFn | SE | Halstead V/D/E | Dup× | SHA | Source |
|--------:|---:|------:|-----:|-------:|---:|----------------|-----:|-----|--------|
| 100 | 0 | 0 | 0 | 0 | 0 | 19.65 / 1.5 / 29.47 | 1 | `7bdf46765e9c` | `#(reduce + 0 (map (constantly 1) %))` |
| 100 | 0 | 0 | 0 | 0 | 0 | ERR(nan) | 1 | `d8a86d191599` | `reduce #(if %2 (inc %1)) 0` |
| 100 | 0 | 0 | 0 | 0 | 0 | ERR(nan) | 1 | `b434677a4469` | `reduce (fn [x y] (+ x 1)) 0` |
| 100 | 0 | 0 | 0 | 0 | 0 | ERR(nan) | 1 | `ad0d6edf2fc6` | `reduce #(+ %1 (if %2 1 1)) 0` |
| 100 | 0 | 0 | 0 | 0 | 0 | 4.75 / 1 / 4.75 | 1 | `06a6c9d15b4e` | `#(.size (vec %) )` |
| 93 | 0 | 1 | 0 | 1 | 0 | 27 / 1.8 / 48.6 | 1 | `54fd943f3b01` | `#( reduce (fn [x y] (+ 1 x)) 0 % )` |
| 93 | 0 | 1 | 0 | 1 | 0 | 19.65 / 1.5 / 29.47 | 1 | `ab8456298ddc` | `#(apply + (map (fn [x] 1) %))` |
| 93 | 0 | 1 | 0 | 1 | 0 | 22.46 / 1.88 / 42.22 | 1 | `01139a2cbf07` | `#(reduce (fn [memo x] (inc memo)) 0 %)` |
| 93 | 0 | 1 | 0 | 1 | 0 | 38.04 / 2.4 / 91.3 | 1 | `61e304a26279` | `(fn [x] (apply max (map second (map vector x (map inc (rang…` |
| 93 | 0 | 1 | 0 | 1 | 0 | 27 / 1.8 / 48.6 | 1 | `ed038d7205cf` | `#(reduce (fn [n e] (+ 1 n)) 0 %)` |
| 93 | 0 | 1 | 0 | 1 | 0 | 27 / 1.8 / 48.6 | 1 | `1f9ecd786d15` | `#(reduce (fn [s e] (+ s 1)) 0 %)` |
| 93 | 0 | 1 | 0 | 1 | 0 | 22.46 / 1.88 / 42.22 | 1 | `a8552094eddc` | `(partial reduce (fn [result item] (inc result)) 0 )` |
| 93 | 0 | 1 | 0 | 1 | 0 | 27 / 1.8 / 48.6 | 1 | `1ea17fc51a7a` | `#(reduce (fn [r x] (+ r 1)) 0 %)` |
| 85 | 0 | 2 | 0 | 2 | 0 | 28.07 / 2.25 / 63.16 | 1 | `f82b920e3e34` | `(fn [coll] (reduce (fn [a, x] (inc a)) 0 coll))` |
| 85 | 0 | 2 | 0 | 2 | 0 | 30 / 1.8 / 54 | 1 | `37988199562a` | `(fn cnt [input] (reduce + (map (fn [_] 1) input)))` |
| 85 | 0 | 2 | 0 | 2 | 0 | 33 / 2.1 / 69.3 | 1 | `bfd6798faded` | `(fn[coll](reduce (fn[x y](+ 1 x)) 0 coll))` |
| 85 | 0 | 2 | 0 | 2 | 0 | 43.19 / 2.67 / 115.32 | 1 | `9b0699963d4f` | `(fn [coll] (reduce (fn [result item] (+ 1 result)) 0 (apply…` |
| 85 | 0 | 2 | 0 | 2 | 0 | 28.07 / 2.25 / 63.16 | 1 | `690716c38942` | `(fn [elements] (reduce (fn [acc el] (inc acc)) 0 elements))` |
| 85 | 0 | 2 | 0 | 2 | 0 | 28.07 / 2.25 / 63.16 | 1 | `d35b93b555b5` | `(fn [li] (reduce (fn [acc i] (inc acc)) 0 li))` |
| 85 | 0 | 2 | 0 | 2 | 0 | 28.07 / 2.25 / 63.16 | 1 | `0d6aa38727c0` | `(fn [coll] (reduce (fn [n _] (inc n)) 0 coll))` |
| 84 | 1 | 2 | 0 | 1 | 0 | 2 / 0.5 / 1 | 1 | `779564fbf315` | `#((partial (fn foo [mycount m] (if (not= (rest m) '()) (foo…` |
| 84 | 1 | 2 | 0 | 1 | 0 | 39.86 / 4.5 / 179.37 | 1 | `d79476858eb7` | `(fn count-it [x] (if (empty? x) 0 (+ 1 (count-it (rest x)))…` |
| 84 | 1 | 2 | 0 | 1 | 0 | 34.87 / 3.75 / 130.76 | 1 | `beeae61fb4a6` | `(fn cnt [xs] (if xs (+ 1 (cnt (next xs))) 0))` |
| 84 | 1 | 2 | 0 | 1 | 0 | 34.87 / 5 / 174.35 | 1 | `88f0b1530765` | `(fn length [seq] (if (empty? seq) 0 (inc (length (rest seq)…` |
| 84 | 1 | 2 | 0 | 1 | 0 | 34.87 / 5 / 174.35 | 1 | `7cba64932495` | `(fn c [seq] (if (empty? seq) 0 (inc (c (rest seq)))))` |
| 84 | 1 | 2 | 0 | 1 | 0 | 39.86 / 4.5 / 179.37 | 1 | `09decd434d4c` | `(fn foo [list] (if (empty? list) 0 (+ (foo (rest list)) 1) …` |
| 84 | 1 | 2 | 0 | 1 | 0 | 62.91 / 6.4 / 402.62 | 1 | `90e005bd89a6` | `(fn f ([s] (f s 0)) ([s n] (if (empty? s) n (f (rest s) (+ …` |
| 84 | 1 | 2 | 0 | 1 | 0 | 44.97 / 5.25 / 236.09 | 1 | `6a032787047a` | `(fn f [s] (if (= '() s) 0 (inc (f (rest s)))))` |
| 84 | 1 | 2 | 0 | 1 | 0 | 34.87 / 3.75 / 130.76 | 1 | `8b51fe009702` | `( fn [x] ( reduce + (map #( if ( = % % ) 1 ) x ) ) )` |
| 84 | 1 | 2 | 0 | 1 | 0 | 34.87 / 5 / 174.35 | 1 | `caa02c5cd887` | `(fn f [s] (if (first s) (inc (f (rest s))) 0))` |
| 84 | 1 | 2 | 0 | 1 | 0 | 34.87 / 2.8 / 97.64 | 1 | `1335539e53f5` | `(fn c [[h & t]] (if t (inc (c t)) 1))` |
| 84 | 1 | 2 | 0 | 1 | 0 | 55.51 / 5.6 / 310.86 | 1 | `c1a051e38057` | `(fn cnt [l] (if (= (first l) nil) (quote 0) (+ 1 (cnt (rest…` |
| 84 | 1 | 2 | 0 | 1 | 0 | 39.86 / 4.5 / 179.37 | 1 | `3908141e674f` | `(fn length [l] (if (empty? l) 0 (+ 1 (length (rest l)))))` |
| 84 | 1 | 2 | 0 | 1 | 0 | 44.97 / 4.2 / 188.87 | 1 | `13bd9f824d42` | `(fn mycount [xs] (cond (empty? xs) 0 :else (+ 1 (mycount (r…` |
| 84 | 1 | 2 | 0 | 1 | 0 | 43.19 / 5.25 / 226.75 | 1 | `29704b95bc32` | `(fn get-count [seq] (if (empty? seq) 0 (+ 1 (get-count (dro…` |
| 84 | 1 | 2 | 0 | 1 | 0 | 36.54 / 2.33 / 85.14 | 1 | `1ad572ce485c` | `(fn [seq] (reduce + 0 (map #(if true 1 %) seq)))` |
| 84 | 1 | 2 | 0 | 1 | 0 | 76.15 / 5.5 / 418.83 | 1 | `e260b1c49ed5` | `(fn cnt [l] (cond (or (= l "") (= l []) (= l nil)) 0 :else …` |
| 84 | 1 | 2 | 0 | 1 | 0 | 46.51 / 5.25 / 244.18 | 1 | `4b1b42b346a1` | `(fn lala [x] (if (= x []) (+ 0 0) (+ 1 (lala (rest x))) ))` |
| 84 | 1 | 2 | 0 | 1 | 0 | 39.86 / 4.5 / 179.37 | 1 | `2252914296b7` | `(fn cnt [coll] (if (= coll []) 0 (+ 1 (cnt (rest coll)))))` |
| 84 | 1 | 2 | 0 | 1 | 0 | 64.73 / 8.75 / 566.39 | 1 | `414e1e21c50b` | `(fn f ([s] (f s 0)) ([s c] (if (nil? (seq s)) c (recur (res…` |
| 77 | 1 | 3 | 0 | 2 | 0 | 10 / 1.5 / 15 | 1 | `60dd0ebd8cad` | `(fn [A] ((fn laske [A size] (if (first A) (laske (rest A) (…` |
| 77 | 1 | 3 | 0 | 2 | 0 | 10 / 1.5 / 15 | 1 | `3fe3dbb134a2` | `(fn [x] ((fn [x c] (if (not (first x)) c (recur (rest x) (i…` |
| 74 | 3 | 2 | 2 | 0 | 0 | 46.51 / 8.17 / 379.99 | 1 | `f973050fdaca` | `#(loop [s % c 0] (if (empty? s) c (recur (rest s) (inc c))))` |
| 74 | 3 | 2 | 2 | 0 | 0 | 57.36 / 8 / 458.88 | 1 | `9ef6dd54e632` | `#(loop [c % n 0] (if (= (first c) nil) n (recur (next c) (i…` |
| 74 | 3 | 2 | 2 | 0 | 0 | 46.51 / 8.17 / 379.99 | 1 | `4637b9088a78` | `#(loop [n 0 l %1] (if (empty? l) n (recur (inc n) (rest l))…` |
| 74 | 3 | 2 | 2 | 0 | 0 | 55.35 / 9.33 / 516.42 | 1 | `36cac10d73d9` | `#(loop [i % c 0] (if (not (seq i)) c (recur (rest (seq i)) …` |
| 74 | 3 | 2 | 2 | 0 | 0 | 51.89 / 7 / 363.23 | 1 | `b4271efe9111` | `#(loop [i 0 c %] (if (seq c) (recur (+ 1 i) (rest c)) i))` |
| 74 | 3 | 2 | 2 | 0 | 0 | 46.51 / 8.17 / 379.99 | 1 | `0d1f4c90fa2f` | `#(loop [x % c 0] (if (seq x) (recur (rest x) (inc c)) c))` |
| 74 | 3 | 2 | 2 | 0 | 0 | 46.51 / 8.17 / 379.99 | 1 | `7994770d24fe` | `#(loop [x 0 col %] (if (empty? col) x (recur (inc x) (rest …` |
| 74 | 3 | 2 | 2 | 0 | 0 | 51.89 / 7 / 363.23 | 1 | `e1a1883b0c7a` | `#(loop [xs % n 0] (if (empty? xs) n (recur (rest xs) (+ n 1…` |
| 74 | 3 | 2 | 2 | 0 | 0 | 46.51 / 8.17 / 379.99 | 1 | `20b34a76d3bc` | `#(loop [cnt 0, lst %] (if (seq lst) (recur (inc cnt) (rest …` |
| 72 | 3 | 2 | 2 | 1 | 0 | 58.81 / 12 / 705.72 | 1 | `9b580fbecb4a` | `(fn [l] (loop [l (seq l) c 0] (if-not (empty? l) (recur (re…` |
| 72 | 3 | 2 | 2 | 1 | 0 | 55.35 / 10.67 / 590.58 | 1 | `492e4e604e23` | `(fn [coll] (loop [coll coll c 0] (if-not (seq coll) c (recu…` |
| 69 | 1 | 4 | 1 | 2 | 0 | 92 / 7 / 644 | 1 | `b7c105579236` | `(fn jct [x] (let [helper (fn h [x n] (cond (empty? x) n :el…` |
| 69 | 3 | 3 | 1 | 1 | 0 | 51.89 / 7 / 363.23 | 1 | `bd549014c8a2` | `(fn [s] (loop [c 0] (if (nil? (nth s c nil)) c (recur (inc …` |
| 67 | 3 | 3 | 2 | 1 | 0 | 57.36 / 8 / 458.88 | 1 | `419b3ec881e4` | `(fn [x] (loop [col x ct 0] (if (nil? col) ct (recur (next c…` |
| 67 | 3 | 3 | 2 | 1 | 0 | 62.91 / 9 / 566.19 | 1 | `860dba17918c` | `(fn [x] (loop [s x acc 0] (if (nil? (first s)) acc (recur (…` |
| 67 | 3 | 3 | 2 | 1 | 0 | 62.91 / 7.2 / 452.95 | 1 | `5789e86a24ad` | `(fn count_elements [s] (loop [ c 0 r s ] (if (empty? r) c (…` |
| 67 | 3 | 3 | 2 | 1 | 0 | 57.36 / 8 / 458.88 | 1 | `711cd1e797c7` | `(fn [sq] (loop [accum sq i 0] (if (seq accum) (recur (next …` |
| 67 | 3 | 3 | 2 | 1 | 0 | 60.94 / 9 / 548.46 | 1 | `015528c3431c` | `(fn laenge [cc] (loop [cc cc wert 0] (if (empty? cc) wert (…` |
| 67 | 3 | 3 | 2 | 1 | 0 | 55.35 / 10.67 / 590.58 | 1 | `c48256167629` | `(fn [coll] (loop [coll coll acc 0] (if (empty? coll) acc (r…` |
| n/a | n/a | n/a | n/a | n/a | n/a | ERR(unreadable) | 1 | `b4e75daf3e91` | `(fn [x] (let [counter (ref 0)] (loop [s x] (when (not= s ni…` |

## Failure modes of the v1 pipeline
- 4 of 62 unique snippets produce no usable Halstead numbers.
  - `d8a86d191599` (nan): `reduce #(if %2 (inc %1)) 0`
  - `b434677a4469` (nan): `reduce (fn [x y] (+ x 1)) 0`
  - `ad0d6edf2fc6` (nan): `reduce #(+ %1 (if %2 1 1)) 0`
  - `b4e75daf3e91` (unreadable): `(fn [x] (let [counter (ref 0)] (loop [s x] (when (not= s nil) (dosync…`

## Rank disagreements
Largest rank disagreements (clarity rank vs. Halstead effort rank, where lower is clearer):
- `d79476858eb7` clarity=#38 halstead=#27:   `(fn count-it [x] (if (empty? x) 0 (+ 1 (count-it (rest x)))…`
- `f973050fdaca` clarity=#50 halstead=#39:   `#(loop [s % c 0] (if (empty? s) c (recur (rest s) (inc c))))`
- `90e005bd89a6` clarity=#33 halstead=#44:   `(fn f ([s] (f s 0)) ([s n] (if (empty? s) n (f (rest s) (+ …`
- `88f0b1530765` clarity=#36 halstead=#24:   `(fn length [seq] (if (empty? seq) 0 (inc (length (rest seq)…`
- `5789e86a24ad` clarity=#58 halstead=#46:   `(fn count_elements [s] (loop [ c 0 r s ] (if (empty? r) c (…`
- `4b1b42b346a1` clarity=#22 halstead=#34:   `(fn lala [x] (if (= x []) (+ 0 0) (+ 1 (lala (rest x))) ))`
- `419b3ec881e4` clarity=#60 halstead=#47:   `(fn [x] (loop [col x ct 0] (if (nil? col) ct (recur (next c…`
- `beeae61fb4a6` clarity=#37 halstead=#22:   `(fn cnt [xs] (if xs (+ 1 (cnt (next xs))) 0))`
