(ns clojurecamp.currmap.routes
  (:require
    [clojurecamp.currmap.db :as db]))

(def routes
  [
   [[:get "/api/data"]
    (fn [_request]
      {:status 200
       :body {:db (db/edn)}})]

   [[:put "/api/data"]
    (fn [request]
      (db/overwrite-from-string! (get-in request [:body-params :db]))
      {:status 200})]])
