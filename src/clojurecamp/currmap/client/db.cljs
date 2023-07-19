(ns clojurecamp.currmap.client.db
  (:require
    [reagent.core :as r]
    [cljs.reader]
    [datascript.core :as d]
    [posh.reagent :as posh]))

;; this ns shouldn't be accessed from views

(defonce data (atom nil))

(defonce ready? (r/atom false))

(defn initialize-db! [db-string]
  (reset! data
          (d/conn-from-db (cljs.reader/read-string db-string)))
  (posh/posh! @data)
  (reset! ready? true))

(defn q
  [query & args]
  (apply posh/q query @data args))

(defn pull'
  [pattern [k v]]
  (let [eid (first (d/q '[:find [?e ...]
                          :in $ ?k ?v
                          :where
                          [?e ?k ?v]]
                        @@data
                        k v))]
    (posh/pull @data pattern eid)))

(defn pull
  [pattern eid]
  (apply posh/pull @data pattern eid))

(defn transact!
  [& args]
  (apply d/transact! @data args))
