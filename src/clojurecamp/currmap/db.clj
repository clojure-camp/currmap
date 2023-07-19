(ns clojurecamp.currmap.db
  (:require
    [clojure.edn :as edn]
    [clojure.java.io :as io]
    [bloom.commons.thread-safe-io :as thread-safe]
    [datascript.core :as d]
    [clojurecamp.currmap.config :as config]
    [clojurecamp.currmap.domain.schema :as schema]))

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

(defn edn
  []
  (-> @data
      d/db
      pr-str))

(defn persist!
  []
  (thread-safe/spit
    (config/get :data-path)
    (edn)))

(defn q
  [query & args]
  (apply d/q query @@data args))

(defn transact!
  [& args]
  (apply d/transact! @data args))

#_(initialize-empty!)
#_(persist!)
#_(initialize-from-file!)
#_(deref data)
