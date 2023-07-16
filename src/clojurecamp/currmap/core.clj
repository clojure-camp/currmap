(ns clojurecamp.currmap.core
  (:gen-class)
  (:require
    [bloom.omni.core :as omni]
    [clojurecamp.currmap.omni-config :refer [omni-config]]
    [clojurecamp.currmap.db :as db]))

(defn start! []
  (omni/start! omni/system omni-config))

(defn stop! []
  (omni/stop!))

(defn -main []
  (db/initialize!)
  (start!))

#_(start!)
