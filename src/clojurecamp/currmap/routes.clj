(ns clojurecamp.currmap.routes
  (:require
    [clojure.string :as string]
    [malli.core :as m]
    [clojurecamp.currmap.db :as db]
    [clojurecamp.currmap.email :as email]
    [clojurecamp.currmap.emails :as emails]
    [clojurecamp.currmap.schema :as schema]))

(defn normalize
  [email-string]
  (some-> email-string
          string/trim
          string/lower-case))

(defn email->user-id [email]
  (db/q
    '[:find ?user-id .
      :in $ ?email
      :where
      [?u :user/email ?email]
      [?u :user/id ?user-id]]
    email))

(def routes
  [
   [[:put "/api/request-auth"]
    (fn [request]
      (let [email (normalize (get-in request [:body-params :email]))]
        (when (m/validate schema/Email email)
          (let [user-id (or (email->user-id email)
                            (do
                              (db/transact!
                                [(merge (schema/blank :user)
                                        {:user/email email})])
                              (email->user-id email)))]
            (email/send!
              (emails/login-link {:user-id user-id
                                  :email email}))
            {:status 200}))))]

   ;; users are logged in via omni's token middleware

   [[:delete "/api/session"]
    (fn [_request]
      {:status 200
       :session nil})]

   [[:get "/api/data"]
    (fn [request]
      {:status 200
       :body {:db (db/edn)
              :user-id (get-in request [:session :user-id])}})]

   [[:put "/api/data"]
    (fn [request]
      (db/overwrite-from-string! (get-in request [:body-params :db]))
      {:status 200})]])
