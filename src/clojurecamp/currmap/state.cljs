(ns clojurecamp.currmap.state
  (:require
    [bloom.commons.debounce :as debounce]
    [bloom.commons.ajax :as ajax]
    [cljs.reader]
    [datascript.core :as d]
    [reagent.core :as r]
    [posh.reagent :as posh]
    [clojurecamp.currmap.schema :as schema]))

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


;; misc ui stuff, regular reagent atoms

(defonce state (r/atom
                 {:db/active-editor-entity nil
                  :db/user {:user/id #uuid "b5048daf-1230-46b1-adcb-89bc5369bc91"}}))

(defn entity-for-editing
  [[id-attr _id :as ident]]
  @(pull'
    (schema/pattern-for (schema/attr->entity-type id-attr))
    ident))

(defn open-editor!
  [starter-entity]
  (swap! state assoc :db/active-editor-entity starter-entity))

(defn close-editor!
  []
  (swap! state assoc :db/active-editor-entity nil))

(def active-editor-entity (r/cursor state [:db/active-editor-entity]))
(def user (r/cursor state [:db/user]))

(defn save-entity!
  [entity]
  (transact! [(->> entity
                   (filter (fn [[_k v]]
                             v))
                   (into {}))]))
