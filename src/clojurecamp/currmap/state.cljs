(ns clojurecamp.currmap.state
  (:require
    [cljs.reader]
    [reagent.core :as r]
    [bloom.commons.debounce :as debounce]
    [bloom.commons.ajax :as ajax]
    [datascript.core :as d]
    [posh.reagent :as posh]))

;; core data, in datascript

(defonce data (r/atom nil))

(defonce ready? (r/reaction (boolean @data)))

(defn persist!
  ;; tx-report
  ;; https://github.com/tonsky/datascript/blob/master/src/datascript/core.cljc#L472
  [{:keys [db-before db-after] :as tx-report}]
  (ajax/request
    {:uri "/api/data"
     :method :put
     :params {:db (pr-str db-after)}
     :on-success (fn []

                   )}))

(def debounced-persist! (debounce/debounce persist! 500))

(defonce _
  (do
    (ajax/request
      {:uri "/api/data"
       :method :get
       :on-success (fn [{:keys [db]}]
                     (reset! data
                             (d/conn-from-db (cljs.reader/read-string db)))
                     (posh/posh! @data)

                     (d/listen! @data ::persist debounced-persist!))})
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


;; misc ui stuff, regular reagent atoms

(defonce state (r/atom
                 {:db/editing-ident nil}))

(defn open-editor!
  [[_entity-id-key _entity-id :as e]]
  (swap! state assoc :db/editing-ident e))

(defn close-editor!
  []
  (swap! state assoc :db/editing-ident nil))

(def active-form-entity (r/cursor state [:db/editing-ident]))

(defn save-entity!
  [entity]
  (transact! [entity]))
