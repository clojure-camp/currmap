(ns clojurecamp.currmap.core
  (:gen-class)
  (:require
    [bloom.omni.core :as omni]
    [clojurecamp.currmap.omni-config :refer [omni-config]]))

(defn start! []
  (omni/start! omni/system omni-config))

(defn stop! []
  (omni/stop!))

(defn -main []
  (start!))

#_(start!)
