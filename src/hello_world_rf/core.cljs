(ns hello-world-rf.core
  (:require [re-frame.core :as re-frame]
            [reagent.dom :as rdom]))

;; -- Event Handlers --
(re-frame/reg-event-fx
 :initialize
 (fn [_ _]
   {:dispatch [:say-hello "World"]}))

(re-frame/reg-event-db
 :say-hello
 (fn [db [_ name]]
   (assoc db :text (str "Hello, " name "!"))))

;; -- Subscriptions --
(re-frame/reg-sub
 :text
 (fn [db _]
   (:text db)))

;; -- Views --
(defn main-panel []
  (let [text (re-frame/subscribe [:text])]
    (fn []
      [:div
       [:h1 @text]])))

;; -- Initialize App --
(defn init []
  (re-frame/dispatch [:initialize])
  (rdom/render [main-panel]
            (.getElementById js/document "app")))
