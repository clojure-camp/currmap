(ns clojurecamp.currmap.routes
  (:require
    [bloom.commons.tada.rpc.server :as tada.rpc]))

(def routes
  [
   [[:post "/api/tada/*"]
    (tada.rpc/make-handler
      {:extra-params (fn [request]
                       {:user-id (get-in request [:session :user-id])})})]

   ;; users are logged in via omni's token middleware

   [[:delete "/api/session"]
    (fn [_request]
      {:status 200
       :session nil})]])
