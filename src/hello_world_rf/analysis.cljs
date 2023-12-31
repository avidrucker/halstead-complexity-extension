(ns hello-world-rf.analysis
  (:require [cljs.reader :as reader]
            [clojure.string :as str]))

;; DONE: make sure this code can handle let bindings
;; DONE: make sure this code can handle fn anonymous functions
;; DONE: make sure this code can handle #() anonymous functions
;; DONE: make sure this code can remove/ignore comments
;; DONE: make sure this code can handle multiline inputs
;; DONE: make sure this code can handle higher order functions
;; Q: how would one measure the code (map inc [1 2 3]), specifically, would inc be an operator, an operand, or both?
;; A: for now, let's go with a strict interpretation that inc is an operand
;; DONE: write function that takes in map of operators and operands and calculates the Halstead Complexity metrics
;; TODO: make sure code can handle 'loop' vectors w/ function call initializations (similar to let bindings)
;; TODO: write a function that will parse an entire program/namespace
;; TODO: write a wrapper that will handle... 

(defn is-operator? 
  "differentiates between things that could be an operator 
   versus things that can't (such as primitives like numbers, 
   strings, etc.)" [s]
  (or (symbol? s) (keyword? s)))

(defn wrap-in-single-quotes [s]
  (str "'" s "'"))

(defn parse-body
  "Parses a body of Clojure code for operators and operands."
  [expr]
  (letfn [(walk [node]
            (cond
              ;; Check if node is a list (function call or special form)
              (list? node)
              (let [op (first node)
                    ;; _ (println "parse-body op:" op)
                    operands (rest node)]
                (if (or (= op 'let) (= op 'loop))  ;; Check for special forms
                  (parse-special node)  ;; Use parse-special for special forms
                  (let [results (map walk operands)  ;; Continue with regular function calls
                        nested-ops (mapcat first results)
                        nested-opds (mapcat second results)]
                    [(cons op nested-ops) nested-opds])))

              ;; Handle strings
              (string? node)
              [[] [(wrap-in-single-quotes node)]]

              ;; Handle other elements
              :else
              (do
                ;; (println "non-opd:" node)
                [[] [node]])))]
    (walk expr)))

(defn parse-let-bindings
  "Parses the bindings in a 'let' or 'loop' form."
  [bindings]
  (let [pairs (partition 2 bindings)
        parse-pair (fn [[sym val]]
                     (let [[val-ops val-opds] (parse-tree val)]
                       [val-ops (cons sym val-opds)]))] ; Pair symbol with value operands
    (reduce (fn [[all-ops all-opds] [ops opds]]
              [(concat all-ops ops) (concat all-opds opds)])
            [[] []]
            (map parse-pair pairs))))


(defn parse-special
  "Parses special forms like 'defn', 'loop', and 'let'."
  [expr]
  (let [[op & args] expr]
    ;; (println "parse-special op is:" op)
    (cond
      (= op 'defn)
      (let [func-name (first args)
            docstring (if (string? (nth args 1))
                        (wrap-in-single-quotes (nth args 1)) nil)
            params (if docstring (nth args 2) (nth args 1))
            body (first (drop (if docstring 3 2) args))
            [body-ops body-opds] (parse-body body)]
        [(cons op body-ops) ; Combine operators
         (concat [func-name] (when docstring [docstring]) [params] body-opds)]) ; Combine operands

      (or (= op 'let) (= op 'loop))
      (do
        ;; (println "parse-special op:" op)
        (let [bindings (first args)
              ;; _ (println "bindings:" bindings)
              body (rest args)
              [binding-ops binding-opds] (parse-let-bindings bindings)
              [body-ops body-opds] (parse-body (first body))]
          [(concat [op] binding-ops body-ops)
           (concat [binding-opds] [body-opds])])))))

(defn parse-tree
  "Traverses a syntax tree and separates operators and operands."
  [tree]
  (letfn [(walk-tree [node]
            (cond

              (list? node)
              (do
                ;; (println "Found list, first element:" (first node))
                (if (or (= (first node) 'defn) (= (first node) 'let) (= (first node) 'loop))
                  (parse-special node)
                  (parse-body node)))

              (vector? node)
              [nil (seq node)]

              (set? node)
              [nil (seq node)]

              (map? node)
              [nil (flatten (seq node))]

              (string? node)
              [[] [(wrap-in-single-quotes node)]]

              :else
              (if (is-operator? node)
                (do
                  ;; (println "operator found:" node)
                  [[node] []])
                (do
                  ;; (println "no operator found:" node)
                  [[] [node]]))))]
    (walk-tree tree)))

;; for dealing w/ #() anonymous functions
(defn replace-hash-parens [s]
  (clojure.string/replace s "#(" "("))

;; for dealing with regex strings
(defn replace-esc-regex-hashes [s]
  (clojure.string/replace s "#\"" "\""))

;; note: this will make sets fail to parse
#_(defn remove-hashes [s]
  (clojure.string/replace s "#" ""))

;; for dealing with regular expressions
(defn remove-slashes [s]
  (clojure.string/replace s "\\" ""))

(defn read-clojure-string [s]
  (try
    (reader/read-string (remove-slashes (replace-esc-regex-hashes (replace-hash-parens s))))
    (catch :default _ nil))) ;; :default exception e doesn't get called so we can call it "_"

(defn separate-operators-operands [code-str]
  (let [read-code (read-clojure-string code-str)]
    (if read-code
      (let [[operators operands] (parse-tree read-code)]
        {:operators operators :operands (flatten operands)})
      (do ;; (println "Invalid Clojure code string")
        {:error "Invalid Clojure code string"}))))

(defn log-base-2 [x]
  (/ (Math/log x) (Math/log 2)))

(defn round-to-2-decimals [num]
  (js/parseFloat (js/Number.prototype.toFixed.call num 2)))

;; https://en.wikipedia.org/wiki/Halstead_complexity_measures
;; n1 = the number of distinct operators
;; n2 = the number of distinct operands
;; N_1 = the total number of operators
;; N_2 = the total number of operands
;; program vocabulary: n = n1 + n2
;; program length: N = N_1 + N_2
;; estimated program length: N' = n1*log_2(n1) + n2*log_2(n2)
;; volume: V = N*log_2(n)
;; difficulty: D = n1/2 * N_2/n2
;; effort: E = D * V
(defn tokens-to-metrics-tuples [{:keys [operators operands]}]
  (let [n1 (count (distinct operators))
        n2 (count (distinct operands))
        N_1 (count operators)
        N_2 (count operands)
        vocab (+ n1 n2)
        length (+ N_1 N_2)
        est-program-length (round-to-2-decimals
                             (+ (* n1 (log-base-2 n1))
                                (* n2 (log-base-2 n2))))
        volume (round-to-2-decimals (* length (log-base-2 vocab)))
        ;; "The difficulty measure is related to the difficulty of the program to write or understand, e.g. when doing code review."
        difficulty (round-to-2-decimals (* (/ n1 2) (/ N_2 n2)))
        effort (round-to-2-decimals (* difficulty volume))
        est-coding-time (round-to-2-decimals (/ effort 18))
        est-bugs (round-to-2-decimals (/ volume 3000))
        ]
    [[:n1 n1 "distinct operators"]
     [:n2 n2 "distinct operands"]
     [:N_1 N_1 "total operators"]
     [:N_2 N_2 "total operands"]
     [:vocab vocab "distinct tokens"] 
     [:length length "total tokens"] 
     [:est-program-length est-program-length] 
     [:volume volume] 
     [:difficulty difficulty] 
     [:effort effort]
     [:est-coding-time est-coding-time "seconds"]
     [:est-bugs est-bugs]]))

(defn tokens-to-metrics-map [{:keys [operators operands]}]
  (let [n1 (count (distinct operators))
        n2 (count (distinct operands))
        N_1 (count operators)
        N_2 (count operands)
        vocab (+ n1 n2)
        length (+ N_1 N_2)
        est-program-length (round-to-2-decimals
                             (+ (* n1 (log-base-2 n1))
                                (* n2 (log-base-2 n2))))
        volume (round-to-2-decimals (* length (log-base-2 vocab)))
        ;; "The difficulty measure is related to the difficulty of the program to write or understand, e.g. when doing code review."
        difficulty (round-to-2-decimals (* (/ n1 2) (/ N_2 n2)))
        effort (round-to-2-decimals (* difficulty volume))
        est-coding-time (round-to-2-decimals (/ effort 18))
        est-bugs (round-to-2-decimals (/ volume 3000))
        ]
    {:n1 n1 ;; distinct operators
     :n2 n2 ;; distinct operands
     :N_1 N_1 ;; total operators
     :N_2 N_2 ;; total operands
     :vocab vocab ;; distinct tokens
     :length length ;; total tokens
     :est-program-length est-program-length 
     :volume volume
     :difficulty difficulty 
     :effort effort
     :est-coding-time est-coding-time
     :est-bugs est-bugs}))

(defn metrics-map-to-short-str [{:keys [volume difficulty effort]}]
  (str "V:" volume " D:" difficulty " E:" effort))

(defn remove-comments 
  "Removes comments from a string of Clojure code."
  [code]
  (let [in-string? (atom false)
        escape-next-char? (atom false)
        skip-line? (atom false)
        result (atom [])]
    (doseq [char code]
      (cond
        ;; If we're at the end of a line, reset skip-line? to false
        (= char \newline) (do (swap! skip-line? (constantly false))
                              (reset! escape-next-char? false)
                              (swap! result conj char))

        ;; If skip-line? is true, skip the character
        @skip-line? nil

        ;; Toggle in-string? when a non-escaped quote is encountered
        (and (= char \") (not @escape-next-char?) (not @skip-line?))
        (do (swap! in-string? not)
            (swap! escape-next-char? (constantly false))
            (swap! result conj char))

        ;; If a semicolon is encountered outside a string, skip the line
        (and (= char \;) (not @in-string?)) (reset! skip-line? true)

        ;; Check for escape character
        (and (= char \\) @in-string?) (do (swap! escape-next-char? not)
                                          (swap! result conj char))

        ;; Add the character to the result
        :else (do (reset! escape-next-char? false)
                  (swap! result conj char))))
    (clojure.string/join @result)))

(defn extract-function-calls 
  "Extracts function calls from a string of Clojure code, returns as a list of function call strings"
  [code]
  (let [code-length (count code)
        stack (atom [])
        function-calls (atom [])]
    (doseq [i (range code-length)]
      (let [char (nth code i)]
        (cond
          (= char \() (swap! stack conj i) ; Open parenthesis
          (= char \)) ; Close parenthesis
          (when (seq @stack)
            (let [start (peek @stack)]
              (swap! stack pop)
              (when (empty? @stack) ; Complete expression
                (swap! function-calls conj (subs code start (inc i)))))))))
    @function-calls))

(defn cd-block-str-to-call-strings [cd-block-str]
  (let [minus-prompts (clojure.string/replace cd-block-str "user=>" "")
        minus-comments (remove-comments minus-prompts)
        call-strings (extract-function-calls minus-comments)]
    call-strings))

(defn combine-operators-and-operands [forms-list]
  (reduce (fn [combined-forms current-form]
            (merge-with concat combined-forms current-form))
          {} forms-list))

(defn call-strings-to-ops-and-opds-map [call-strings]
  (combine-operators-and-operands (map separate-operators-operands call-strings)))

(defn codedocs-code-block-to-short-metrics
  "Takes in a string of Clojure code, removes comments, and returns a short string of Halstead Complexity metrics
   note: this function is meant for use on code blocks from the clojuredocs.org website"
  [cd-code-block]
  (let [call-strings (cd-block-str-to-call-strings cd-code-block) 
        ops-and-opds-map (call-strings-to-ops-and-opds-map call-strings)
        metrics-map (tokens-to-metrics-map ops-and-opds-map)
        short-metrics-map (metrics-map-to-short-str metrics-map)]
    short-metrics-map))

(defn text-input-to-short-metrics
  "Takes in a string of Clojure code, removes comments, and returns a short string of Halstead Complexity metrics
   note: this function is meant for use on code blocks from the 4clojure website"
  [text-input]
  (->> text-input
       separate-operators-operands
       tokens-to-metrics-map
       metrics-map-to-short-str))