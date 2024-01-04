(ns hello-world-rf.analysis-test
  (:require [cljs.test :refer-macros [deftest is testing]]
            [hello-world-rf.analysis :refer [separate-operators-operands]]))

(deftest separate-ops-opds-test

  (testing "simple function calls"
    (is (= {:operators ['+] :operands [1 2 3]}
           (separate-operators-operands "(+ 1 2 3)")))
    (is (= {:operators ['+ '*] :operands [1 2 3]}
           (separate-operators-operands "(+ 1 (* 2 3))")))
    (is (= {:operators ['+ '* '/] :operands [1 2 3 4]}
           (separate-operators-operands "(+ 1 (* (/ 2 3) 4))"))))

  (testing "function definitions"
    (is (= {:operators ['defn 'println] :operands ['hi "'Hi!'"]}
           (separate-operators-operands "(defn hi [] (println \"Hi!\"))")))
    (is (= {:operators ['defn 'println '+] :operands ['add 'x 'y 'x 'y]}
           (separate-operators-operands "(defn add [x y] (println (+ x y)))")))
    (is (= {:operators ['defn 'println '- '/ '+] :operands ['do-math "'does some math'" 'w 'x 'y 'z 'w 'x 'y 'z]}
           (separate-operators-operands "(defn do-math \"does some math\" [w x y z] (println (- w (/ x (+ y z)))))"))))

  (testing "single tokens"
    (is (= {:operators ['reduce] :operands []}
           (separate-operators-operands "reduce")))
    (is (= {:operators [] :operands [5]}
           (separate-operators-operands "5")))
    (is (= {:operators [] :operands ["'cheese'"]}
           (separate-operators-operands "\"cheese\""))))

  (testing "anonymous functions"
    (is (= {:operators ['fn '+] :operands ['x 'x 5]}
           (separate-operators-operands "(fn [x] (+ x 5))")))
    (is (= {:operators ['+] :operands ['5 '%]}
           (separate-operators-operands "#(+ 5 %)"))))

  (testing "let bindings"
    (is (= {:operators ['let 'println '+] :operands ['x 5 'y 10 'x 'y]}
           (separate-operators-operands "(let [x 5 y 10] (println (+ x y)))")))
    (is (= {:operators ['let '+ '*] :operands ['x 5 'y 2 3 'x 'y]}
           (separate-operators-operands "(let [x 5 y (+ 2 3)] (* x y))"))))

  (testing "cases where tokens can be both operator and operand"
    (is (= {:operators ['let 'fn '+ 'println 'z] :operands ['z 3 5]}
           (separate-operators-operands "(let [z (fn [] (+ 3 5))] (println (z)))"))))

  (testing "def bindings"
    (is (= {:operators ['def] :operands ['a 25]}
           (separate-operators-operands "(def a 25)"))))

  (testing "sets"
    (is (= {:operators [] :operands [1 2 3]}
           (separate-operators-operands "#{1 2 3}"))))

  (testing "maps"
    (is (= {:operators [] :operands [:a 5 :b 3 :c 1]}
           (separate-operators-operands "{:a 5 :b 3 :c 1}"))))

  (testing "vectors"
    (is (= {:operators [] :operands [5 6 7 8]}
           (separate-operators-operands "[5 6 7 8]"))))

  (testing "higher order functions (map, filter, reduce, etc.)"
    (is (= {:operators ['map] :operands ['inc 1 2 3]}
           (separate-operators-operands "(map inc [1 2 3])"))))

  (testing "multi-line inputs"
    (is (= {:operators ['defn 'println '+ '*] :operands ['my-func "'a doc-string for my-func'" 'a 'b 'c 'a 'b 'c]}
           (separate-operators-operands "(defn my-func
                                        \"a doc-string for my-func\"
                                        [a b c]
                                        (println (+ a (* b c))))"))))

  (testing "multi-line inputs w/ comments on their own line"
    (is (= {:operators ['defn 'println '+ '*] :operands ['my-func-2 "'a doc-string for my-func-2'" 'a 'b 'c 'a 'b 'c]}
           (separate-operators-operands "(defn my-func-2
                                        \"a doc-string for my-func-2\"
                                        [a b c]
                                        ;; I am a comment on a line by myself
                                        (println (+ a (* b c))))"))))

  (testing "multi-line inputs w/ comments on the same lines as code"
    (is (= {:operators ['defn 'println '+ '*] :operands ['my-func-3 "'a doc-string for my-func-3'" 'a 'b 'c 'a 'b 'c]}
           (separate-operators-operands "(defn my-func-3
                                        \"a doc-string for my-func-3\"
                                        [a b c]
                                        (println ; I am a comment on the same line as code
                                         (+ a (* b c))))"))))

(testing "multi-line inputs with comments preceding the code"
  (is (= {:operators ['defn 'println '+ '*] :operands ['my-func-4 "'a doc-string for my-func-4'" 'a 'b 'c 'a 'b 'c]}
         (separate-operators-operands ";; I am a comment preceding the code
                                       (defn my-func-4
                                        \"a doc-string for my-func-4\"
                                        [a b c]
                                        (println (+ a (* b c))))"))))

  (testing "loop bindings"
    (is (= {:operators ['loop 'if '> 'println 'recur 'inc '+] :operands ['iter 1 'acc 0 'iter 10 'acc 'iter 'acc 'iter]}
           (separate-operators-operands "(loop [iter 1
                                                acc  0]
                                         (if (> iter 10)
                                           (println acc)
                                           (recur (inc iter) (+ acc iter))))"))))
  )

