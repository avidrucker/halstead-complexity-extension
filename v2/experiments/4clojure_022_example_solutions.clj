;; 4clojure problem 22 — "Count a Sequence"
;; Difficulty: easy
;; Source: https://4clojure.oxal.org/problem/22
;;
;; Problem statement:
;;
;;   Write a function which returns the total number of elements in a sequence.
;;
;;   (= (__ '(1 2 3 3 1)) 5)
;;   (= (__ "Hello World") 11)
;;   (= (__ [[1 2] [3 4] [5 6]]) 3)
;;   (= (__ '(13)) 1)
;;   (= (__ '(:a :b :c)) 3)
;;
;;   Special Restrictions: count
;;
;; This file is the input dataset for the v2 clarity-clj experiment.
;; The data is a vector of source strings — one solution per element. The
;; runner reads this file with cljs.reader/read-string, so the form below
;; must be a single EDN value (a vector of strings).

[
"#(loop [i % c 0]
   (if (not (seq i))
     c
     (recur (rest (seq i)) (inc c))))"

"reduce #(+ %1 (if %2 1 1)) 0"

"#(loop [s %
          c 0]
    (if (empty? s)
      c
      (recur (rest s) (inc c))))"

"#(reduce (fn [s e] (+ s 1)) 0 %)"

"(fn [sq]
  (loop [accum sq
         i 0]
    (if (seq accum)
      (recur (next accum) (inc i))
      i)))"

"(fn [l]
  (loop [l (seq l) c 0]
    (if-not (empty? l)
      (recur (rest l) (inc c))
      c)))"

"(fn cnt [coll]
  (if (= coll [])
    0
    (+ 1 (cnt (rest coll)))))"

"#(reduce (fn [n e] (+ 1 n)) 0 %)"

"(fn cnt [input] (reduce + (map (fn [_] 1) input)))"

"#(reduce + 0 (map (constantly 1) %))"

"(fn [coll] (loop [coll coll acc 0] (if (empty? coll) acc (recur (rest coll) (inc acc)))))"

"(fn[coll](reduce (fn[x y](+ 1 x)) 0 coll))"

"#(reduce (fn [memo x] (inc memo)) 0 %)"

"(fn f [s] (if (= '() s) 0 (inc (f (rest s)))))"

"#(reduce (fn [r x] (+ r 1)) 0 %)"

"(fn [coll]
   (loop [coll coll c 0]
     (if-not (seq coll)
       c
       (recur (rest coll) (inc c)))))"

"#(loop [x 0
            col %]
       (if (empty? col)
           x
           (recur (inc x)
                  (rest col))))"

"(fn [coll]
  (reduce (fn [a, x] (inc a)) 0 coll))"

"(fn [coll]
  (reduce (fn [n _] (inc n)) 0 coll))"

"#(loop [i 0 c %] (if (seq c) (recur (+ 1 i) (rest c)) i))"

"(fn get-count [seq]
  (if (empty? seq)
    0
    (+ 1 (get-count (drop 1 seq)))))"

"(fn cnt [xs]
  (if xs
    (+ 1 (cnt (next xs)))
    0))"

"(fn length [seq]
      (if (empty? seq)
      0
      (inc (length (rest seq)))) )"

"#(loop [n 0 l %1] (if (empty? l) n (recur (inc n) (rest l))))"

"#(loop [x %
         c 0]
   (if (seq x)
     (recur (rest x) (inc c))
     c))"

"( fn [x] ( reduce + (map #( if ( = % % ) 1 ) x ) ) )"

"#(apply + (map (fn [x] 1) %))"

"(fn cnt [l] (cond (or (= l \"\") (= l []) (= l nil)) 0 :else (+ 1 (cnt (rest l)))))"

"(fn laenge [cc]
  (loop [cc cc wert 0]
    (if (empty? cc)
      wert
      (recur (rest cc) (inc wert)))))"

"(fn [x] (loop [col x ct 0] (if (nil? col) ct (recur (next col) (inc ct)))))"

"(fn f
  ([s]
   (f s 0))
  ([s c]
   (if (nil? (seq s))
     c
     (recur (rest s) (inc c)))))"

"(partial reduce (fn [result item] (inc result)) 0 )"

"(fn [x]
  ((fn [x c]
    (if (not (first x))
      c
      (recur (rest x) (inc c))))
  x 0))"

"(fn [A]
  ((fn laske [A size]
     (if (first A)
       (laske (rest A) (inc size))
       size))
   A 0))"

"(fn [li]
      (reduce (fn [acc i] (inc acc)) 0 li))"

"(fn f
  ([s] (f s 0))
  ([s n]
   (if (empty? s)
     n
     (f (rest s) (+ n 1)))))"

"#((partial (fn foo [mycount m]
  (if (not= (rest m) '())
  (foo (+ mycount 1) (rest m))
  mycount
)
) 1) %)"

"(fn foo [list] (if
             (empty? list)
             0
             (+ (foo (rest list)) 1)
             )
  )"

"(fn [seq] (reduce + 0 (map #(if true 1 %) seq)))"

"#(loop [cnt 0, lst %]
   (if (seq lst)
     (recur (inc cnt) (rest lst))
     cnt))"

"(fn count-it [x] (if (empty? x) 0 (+ 1 (count-it (rest x)))))"

"(fn [s]
  (loop [c 0]
    (if (nil? (nth s c nil))
      c
      (recur (inc c)))))"

"(fn lala [x]
  (if (= x [])
    (+ 0 0)
    (+ 1 (lala (rest x)))
    ))"

"(fn mycount [xs]
  (cond
    (empty? xs) 0
    :else (+ 1 (mycount (rest xs)))))"

"(fn c [[h & t]] (if t (inc (c t)) 1))"

"(fn f [s] (if (first s) (inc (f (rest s))) 0))"

"(fn [x]
  (let [counter (ref 0)]

      (loop [s x]
        (when (not= s nil)
          (dosync (alter counter inc))
          (recur (next s))
        )
     )
     @counter
  )
)"

"#(loop [c % n 0]
  (if (= (first c) nil)
      n
      (recur (next c) (inc n))))"

"#( reduce (fn [x y] (+ 1 x)) 0 %  )"

"(fn cnt [l] (if (= (first l) nil) (quote 0) (+ 1 (cnt (rest l)))))"

"(fn [x]
    (apply max (map second
                    (map vector
                         x
                         (map inc
                              (range))))))"

"(fn count_elements [s]
  (loop [ c 0
          r s ]
    (if (empty? r)
      c
      (recur
        (inc c)
        (rest r)))))"

"(fn [x] (loop [s x acc 0]
            (if (nil? (first s))
              acc
              (recur (rest s) (inc acc))
              )
            )
  )"

"#(loop [xs %
        n 0]
   (if (empty? xs)
     n
     (recur (rest xs) (+ n 1))))"

"reduce (fn [x y] (+ x 1))  0"

"(fn length [l]
  (if (empty? l)
        0
        (+ 1 (length (rest l)))))"

"(fn [elements] (reduce (fn [acc el] (inc acc)) 0 elements))"

"(fn c [seq]
  (if (empty? seq)
    0
    (inc (c (rest seq)))))"

"(fn jct [x] (let [helper
                  (fn h [x n] (cond (empty? x) n :else (h (rest x) (+ 1 n))))]
              (helper x 0)))"

"reduce #(if %2 (inc %1)) 0"

"(fn [coll]
  (reduce (fn [result item] (+ 1 result)) 0 (apply list coll)))"

"#(.size (vec %) )"
]
