(ns clojurecamp.currmap.db
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [bloom.commons.thread-safe-io :as thread-safe]
    [datascript.core :as d]
    [clojurecamp.currmap.config :as config]
    [clojurecamp.currmap.domain.schema :as schema]))

;; calling this "data"
;; it stores a "conn" (atom) that stores a "db"
(defonce data (atom nil))

(defn overwrite-from-string! [s]
  (reset! data (-> (edn/read-string {:readers d/data-readers}
                                    s)
                   (d/conn-from-db))))

(defn initialize-empty! []
  (reset! data
          (d/create-conn schema/datascript-schema)))

(defn initialize-from-file! []
  (overwrite-from-string! (thread-safe/slurp (config/get :data-path))))

(defn initialize! []
  (if (.exists (io/file (config/get :data-path)))
    (initialize-from-file!)
    (initialize-empty!)))

(defn ->edn
  [db]
  (pr-str db))

;; user data contains email PII
(defn filter-users
  [db]
  (d/filter db
            (fn [_db datom]
              (not= :user/email (:a datom)))))

(defn persist!
  []
  (thread-safe/spit
    (config/get :data-path)
    (->edn (d/db @data))))

(defn q
  [query & args]
  (apply d/q query @@data args))

(defn transact!
  [& args]
  (apply d/transact! @data args))

(defn pull-ident
  [pattern [k v]]
  (let [eid (d/q '[:find ?e .
                   :in $ ?k ?v
                   :where
                   [?e ?k ?v]]
                 @@data
                 k v)]
    (d/pull @@data pattern eid)))

#_(initialize-empty!)
#_(persist!)
#_(initialize-from-file!)
#_(deref data)
