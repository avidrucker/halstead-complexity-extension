(ns hello-world-rf.events
  (:require
   [re-frame.core :as re-frame]
   [hello-world-rf.db :as db]
   ))

(re-frame/reg-event-db
 ::initialize-db
 (fn [_ _]
   db/default-db))
