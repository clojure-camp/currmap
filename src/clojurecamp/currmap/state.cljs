(ns clojurecamp.currmap.state
  (:require
    [cljs.reader]
    [reagent.core :as r]
    [bloom.commons.ajax :as ajax]
    [datascript.core :as d]
    [posh.reagent :as posh]))

(defonce data (r/atom nil))

(defonce ready? (r/reaction (boolean @data)))

(defonce _
  (do
    (ajax/request
      {:uri "/api/data"
       :method :get
       :on-success (fn [{:keys [db]}]
                     (reset! data
                             (d/conn-from-db (cljs.reader/read-string db)))
                     (posh/posh! @data))})
    nil))

(defn q
  [pattern & args]
  (apply posh/q pattern @data args))

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


