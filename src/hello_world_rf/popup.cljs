(ns hello-world-rf.popup
  (:require [reagent.dom :as rdom]))

(defn popup-component []
  [:div "Hello from Popup!"])

(defn init []
  (rdom/render [popup-component]
                  (.getElementById js/document "popup")))

(init)
