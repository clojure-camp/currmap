(ns clojurecamp.currmap.domain.cqrs
  (:require
    [clojure.string :as string]
    [malli.core :as m]
    [tada.events.core :as tada]
    [clojurecamp.currmap.config :as config]
    [clojurecamp.currmap.db :as db]
    [clojurecamp.currmap.email :as email]
    [clojurecamp.currmap.emails :as emails]
    [clojurecamp.currmap.domain.schema :as schema]))

(defn normalize
  [email-string]
  (some-> email-string
          string/trim
          string/lower-case))

(defn email->user-id
  [email]
  (db/q
    '[:find ?user-id .
      :in $ ?email
      :where
      [?u :user/email ?email]
      [?u :user/id ?user-id]]
    email))

(defn user-exists?
  [user-id]
  (boolean (db/q
             '[:find ?u .
               :in $ ?user-id
               :where
               [?u :user/id ?user-id]]
             user-id)))

(def commands
  [{:id :request-auth!
    :params {:email (fn [email]
                      (m/validate schema/Email email))
             :user-id nil?}
    :conditions
    (fn [{:keys [email]}]
      [[#(contains? (config/get :email-allowlist) email) :unauthorized "Email not in whitelist"]])
    :effect
    (fn [{:keys [email]}]
      (let [email (normalize email)
            user-id (or (email->user-id email)
                        (do
                          (db/transact!
                            [(merge (schema/blank :user)
                                    {:user/email email})])
                          (email->user-id email)))]
        (email/send!
          (emails/login-link {:user-id user-id
                              :email email}))
        {:status 200}))}

   {:id :upsert-entity!
    :params {:user-id uuid?
             :entity schema/valid?}
    :conditions
    (fn [{:keys [user-id]}]
      [[#(user-exists? user-id) :unauthorized "User not authorized"]])
    :effect
    (fn [{:keys [entity]}]
      (db/transact! [entity])
      (db/persist!))}])

(def queries
  [{:id :data
    :params {:user-id (fn [e]
                        (or (nil? e)
                            (uuid? e)))}
    :return
    (fn [{:keys [user-id]}]
      {:db (db/->edn (db/filter-users @@db/data))
       :user-id user-id})}])

(tada/register! (concat commands queries))
