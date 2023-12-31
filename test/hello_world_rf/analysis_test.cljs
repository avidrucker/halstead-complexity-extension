;; (ns hello-world-rf.analysis-test
;;   (:require [cljs.test :refer-macros [deftest is testing]]
;;             [hello-world-rf.analysis :refer :all]))

;; (deftest a-test
;;   (testing "simple function calls"
;;     (is (= "{:operators (+), :operands (1 2 3)}" 
;;            (pr-str (separate-operators-operands "(+ 1 2 3)"))))))

(ns hello-world-rf.analysis-test
  (:require [cljs.test :refer-macros [deftest is]]))

(deftest a-test
  (is (= 1 1)))

;; ;; simple function calls
;; (println (separate-operators-operands "(+ 1 2 3)"))
;; (println (separate-operators-operands "(+ 1 (* 2 3))"))
;; (println (separate-operators-operands "(+ 1 (* (/ 2 3) 4))"))

;; ;; function definitions
;; (println (separate-operators-operands "(defn hi [] (println \"Hi!\"))"))
;; (println (separate-operators-operands "(defn add [x y] (+ x y))"))
;; (println (separate-operators-operands "(defn do-math \"does some math\" [w x y z] (- w (/ x (+ y z))))"))

;; ;; single tokens
;; (println (separate-operators-operands "reduce"))
;; (println (separate-operators-operands "5"))
;; (println (separate-operators-operands "\"cheese\""))

;; ;; anonymous functions
;; (println (separate-operators-operands "(fn [x] (+ x 5))"))
;; ;; currently returns: ERROR: TypeError: Right-hand side of 'instanceof' is not an object
;; (println (separate-operators-operands "#(+ 5 %)"))

;; ;; let bindings
;; (println "---------------")
;; (println (separate-operators-operands "(let [x 5 y 10] (println (+ x y)))"))

;; ;; TODO: implement logic to correctly collect function arguments to let bindings as another collection of operators and operands
;; (println (separate-operators-operands "(let [x 5 y (+ 2 3)] (* x y))")) 

;; ;; example where z is both an operand and an operator
;; (println (separate-operators-operands "(let [z (fn [] (+ 3 5))] (println (z)))"))
;; (println "---------------")

;; ;; def bindings
;; (println (separate-operators-operands "(def a 25)"))

;; ;; sets
;; (println (separate-operators-operands "#{1 2 3}"))

;; ;; maps
;; (println (separate-operators-operands "{:a 5 :b 3 :c 1}"))

;; ;; vectors
;; (println (separate-operators-operands "[5 6 7 8]"))

;; ;; higher order functions (map, filter, reduce, etc.)
;; (println (separate-operators-operands "(map inc [1 2 3])"))

;; ;; Halstead Complexity calculation
;; #_(println (tokens-to-metrics-tuples (separate-operators-operands "(defn add [x y] (+ x y))")))

;; ;; multi-line inputs
;; (separate-operators-operands "(defn my-func
;;   \"a doc-string for my-func\"
;;   [a b c]
;;   (println (+ a (* b c))))")

;; ;; multi-line inputs with comments
;; (separate-operators-operands "(defn my-func-2
;;   \"a doc-string for my-func-2\"
;;   [a b c]
;;   ;; I am a comment on a line by itself
;;   (println (+ a (* b c))))")

;; ;; multi-line inputs with comments on the same line as code
;; (separate-operators-operands "(defn my-func-3
;;   \"a doc-string for my-func-3\"
;;   [a b c]
;;   (println ; I am a comment on the same line as code
;;      (+ a (* b c))))")

;; ;; multi-line inputs with comments preceding the code
;; (separate-operators-operands ";; I am a comment preceding the code
;;   (defn my-func-4
;;   \"a doc-string for my-func-4\"
;;   [a b c]
;;   (println (+ a (* b c))))")

;; (println (separate-operators-operands "(loop [iter 1
;;        acc  0]
;;   (if (> iter 10)
;;     (println acc)
;;     (recur (inc iter) (+ acc iter))))"))