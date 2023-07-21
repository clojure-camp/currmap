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

(defn user-id->email
  [user-id]
  (db/q
    '[:find ?email .
      :in $ ?user-id
      :where
      [?u :user/id ?user-id]
      [?u :user/email ?email]]
    user-id))

(defn user-id->role
  [user-id]
  (let [email (user-id->email user-id)
        {:role/keys [admin student]} (config/get :email-allowlist)]
    (cond
      (contains? admin email) :role/admin
      (contains? student email) :role/student)))

#_(user-id->role (email->user-id "alice@example.com"))

(defn user-exists?
  [user-id]
  (boolean (db/q
             '[:find ?u .
               :in $ ?user-id
               :where
               [?u :user/id ?user-id]]
             user-id)))

(defn remove-nil-values [m]
  (->> m
       (filter (fn [[_k v]]
                 v))
       (into {})))

(def commands
  [{:id :request-auth!
    :params {:email (fn [email]
                      (m/validate schema/Email email))
             :user-id nil?}
    :conditions
    (fn [{:keys [email]}]
      [[#(or (contains? (:role/admin (config/get :email-allowlist)) email)
             (contains? (:role/student (config/get :email-allowlist)) email))
        :unauthorized "Email not in whitelist"]])
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
    (fn [{:keys [user-id entity]}]
      [[#(user-exists? user-id) :unauthorized "User not authorized"]
       [#(schema/can-edit? entity user-id
                           (user-id->role user-id))
        :unauthorized "User not authorized to upsert this entity"]])
    :effect
    (fn [{:keys [entity]}]
      (db/transact! [(remove-nil-values entity)])
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
