(ns clojurecamp.currmap.core
  (:gen-class)
  (:require
    [bloom.omni.core :as omni]
    [clojurecamp.currmap.omni-config :refer [omni-config]]
    [clojurecamp.currmap.seed :as seed]))

(defn start! []
  (omni/start! omni/system omni-config))

(defn stop! []
  (omni/stop!))

(defn -main []
  (start!))

#_(seed/seed!)
#_(start!)
