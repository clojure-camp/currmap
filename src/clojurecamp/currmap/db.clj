(ns clojurecamp.currmap.db
  (:require
   [dat.api :as dat]
   [datascript.core :as d]
   [clojurecamp.currmap.config :as config]
   [clojurecamp.currmap.domain.schema :as schema]))

(defonce db (atom nil))

(defn initialize! []
  (reset! db
          (dat/init! :dat.db/datascript
                     schema/schema
                     {:file-path (config/get :data-path)})))

(defn filter-users
  [derefed-db]
  ;; TODO d/filter should be moved to dat
  (d/filter @(::dat/conn @derefed-db)
            (fn [_db datom]
              (not= :user/email (:a datom)))))

(defn q
  [query & args]
  (apply dat/q query @@db args))

(defn transact!
  [txs]
  (dat/transact! @db txs))

(defn pull-ident
  [pattern [k v]]
  (when-let [eid (dat/q '[:find ?e .
                          :in $ ?k ?v
                          :where
                          [?e ?k ?v]]
                        @@db k v)]
    (dat/pull @@db pattern eid)))

#_(initialize!)
#_(deref (::dat/conn @@db))

