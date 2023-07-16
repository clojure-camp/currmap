(ns clojurecamp.currmap.routes
  (:require
    [clojurecamp.currmap.db :as db]))

(def routes
  [
   [[:get "/api/data"]
    (fn [_request]
      {:body {:db (db/edn)}})]])
