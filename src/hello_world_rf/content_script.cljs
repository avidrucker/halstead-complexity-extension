(ns hello-world-rf.content-script
  (:require [hello-world-rf.analysis :refer [codedocs-code-block-to-short-metrics text-input-to-short-metrics]]))

(defn is-on-clojuredocs? []
  (re-find #"clojuredocs.org" (.-href (.-location js/window))))

(defn is-on-4clojure? []
  (re-find #"4clojure.oxal.org" (.-href (.-location js/window))))

;; if on clojuredocs.org or 4clojure.oxal.org, make a list of all the code examples on the page 
;; - clojuredocs.org code samples are each in divs with the class "example-body" inside of divs
;;   with the class "var-example", we will save these parent divs in a list called "code-examples"
;; - 4clojure.oxal.org code examples are each inside of code tags which are themselves nested in
;;   a pre tag inside of a div tag

;; first, we need to save which page we are on
(defn get-page []
  (if (is-on-clojuredocs?)
    "clojuredocs"
    (if (is-on-4clojure?)
      "4clojure"
      "other")))

;; next, we need to get the code examples(
(defn get-code-examples []
  (let [page (get-page)]
    (case page
      "clojuredocs" (js/document.getElementsByClassName "var-example")
      "4clojure" (js/document.getElementsByTagName "pre")
      "other" "other")))

;; Q: how can I get a node within a node, for example, I know that the inner node will
;; have a class of "example-body"
;; A: I can use the querySelector method on the parent node to get the child node
;; for example, if I have the parent node already as an argument like below, I can do
;; (.querySelector parent-node ".example-body")

(defn append-halstead-to-node-for-4clojure [node]
  (let [halstead (text-input-to-short-metrics (.-textContent node))
        div (js/document.createElement "div")]
    (set! (.-style.backgroundColor div) "green")
    (set! (.-style.color div) "black")
    (set! (.-style.position div) "absolute")
    (set! (.-style.top div) "0")
    (set! (.-style.right div) "0")
    (set! (.-class div) "halstead")
    (set! (.-textContent div) halstead)
    (set! (.-style.position node) "relative")
    (.appendChild node div)))

(defn append-halstead-to-node-for-clojuredocs [node]
  (let [halstead (codedocs-code-block-to-short-metrics (.-textContent (.querySelector node ".example-body")))
        div (js/document.createElement "div")]
    (set! (.-style.backgroundColor div) "blue")
    (set! (.-style.color div) "black")
    (set! (.-style.position div) "absolute")
    (set! (.-style.top div) "0")
    (set! (.-style.right div) "0")
    (set! (.-class div) "halstead")
    (set! (.-textContent div) halstead)
    (set! (.-style.position node) "relative")
    (.appendChild node div)))

(defn append-info-to-node
  "a test function which simply counts the number of characters in the node and appends
   that number to the node as a div with a red background and black text"
  [node]
  (let [char-count (str (.-length (.-textContent node)))
        div (js/document.createElement "div")]
    (set! (.-style.backgroundColor div) "red")
    (set! (.-style.color div) "black")
    (set! (.-style.position div) "absolute")
    (set! (.-style.top div) "0")
    (set! (.-style.right div) "0")
    (set! (.-class div) "char-count")
    (set! (.-textContent div) char-count)
    (set! (.-style.position node) "relative")
    (.appendChild node div)))

(defn process-code-node [node page-context]
  (case page-context
    "clojuredocs" (append-halstead-to-node-for-clojuredocs node)
    "4clojure" (append-halstead-to-node-for-4clojure node)
    "other" "other"))

(defn remove-last-element-child
  "Clones the given node and removes the last element child from the clone.
   This is used in the context that we are appending to each element a div 
   with metrics, but we don't want to count it as part of the code."
  [node]
  (let [clone (.cloneNode node true)] ; Clone the node deeply
    (.removeChild clone (.-lastElementChild clone)) ; Remove the last element child from the clone
    clone)) ; Return the modified clone

;; next, we need to get the code from the code examples
(defn get-code-from-code-examples []
  (let [code-example-nodes (get-code-examples)
        page-context (get-page)
        _ (js/console.log "Nodes count: " (.-length code-example-nodes))]
    (for [code-example (array-seq code-example-nodes)]
      (do
        ;; add infos to the code example
        (process-code-node code-example page-context)
        ;; then return the text content minus the appended info node
        (.-textContent (remove-last-element-child code-example))))))

;; for now, let's simply print out the code examples to the console
(defn print-code-examples []
  (let [code-examples (get-code-from-code-examples) 
        _ (js/console.log "Text examples count: " (count code-examples))]
    (doseq [code-example code-examples]
      (js/console.log code-example))))

(defn run-script []
  (let [on-page-loaded (fn []
                         (js/console.log (str "Content script running on " (.-href (.-location js/window))))
                         (js/console.log (str "Page is '" (get-page) "'"))
                         (print-code-examples))]
    (js/setTimeout (fn []
                     (if (= (.-readyState js/document) "loading")
                       (.addEventListener js/document "DOMContentLoaded" on-page-loaded)
                       (on-page-loaded)))
                   2000)))