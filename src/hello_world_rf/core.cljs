(ns hello-world-rf.core
  (:require [re-frame.core :as re-frame]
            [reagent.dom :as rdom]))

;; -- Event Handlers --
;; This is the only place where you can change the application state.
;; this event handler is called by the :initialize event
;; which is triggered by the init function below.
;; What this event handler does is to set the :text key in the app-db.
;; app-db is the application state, which is a map that contains the application data
;; the reg-event-fx function is used to register an event handler
;; that will change the application state.
(re-frame/reg-event-fx
 :initialize
 (fn [_ _]
   ;; the dispatch keyword is used to dispatch another event
   ;; in this case, we are dispatching the :say-hello event
   {:dispatch [:say-hello "World"]}))

;; this event handler is called by the :initialize event handler
;; which is triggered by the init function below
;; the reg-event-db function is used to register an event handler
;; that will change the application state
;; the event handler is a function that takes two arguments
;; the first argument is the current application state
;; the second argument is the event vector
;; the event vector is a vector of the event keyword and the event arguments
(re-frame/reg-event-db
 :say-hello
 (fn [db [_ name]]
   ;; the assoc function is used here to update the :text key value in app-db
   (assoc db :text (str "Hello, " name "!"))))

;; -- Subscriptions --
;; the reg-sub function is used to register a subscription
;; a subscription is a function that takes two arguments
;; the first argument is the current application state
;; the second argument is the subscription vector
;; Subscriptions in re-frame in general do the following:
;; 1. Take the application state
;; 2. Extract the data from the application state
;; 3. Return the extracted data
(re-frame/reg-sub
 :text
 (fn [db _] 
   (:text db)))

;; -- Views --
(defn main-panel []
  ;; the subscribe function is used to subscribe to a subscription
  ;; the subscription vector is passed as the first argument
  ;; the subscription vector is a vector of the subscription keyword and the subscription arguments
  ;; in this case, the subscription vector is [:text], and there are no further arguments
  ;; @text derefs the atom returned by the subscribe function
  (let [text (re-frame/subscribe [:text])]
    (fn []
      [:div
       [:h1 @text]])))

;; -- Initialize App --
(defn init []
  (re-frame/dispatch [:initialize])
  (rdom/render [main-panel]
            (.getElementById js/document "app")))
