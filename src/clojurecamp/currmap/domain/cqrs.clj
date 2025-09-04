(ns clojurecamp.currmap.domain.cqrs
  (:require
    [bloom.commons.uuid :as uuid]
    [clojure.string :as string]
    [clojure.set :as set]
    [clojure.walk :as walk]
    [malli.core :as m]
    [tada.events.core :as tada]
    [clojurecamp.currmap.ai :as ai]
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

(defn prep-for-transact
  "Remove nil values, because datascript does not allow them."
  [m]
  (walk/postwalk
    (fn [x]
      (if (map? x)
        (->> (dissoc x :db/id)
             (remove (fn [[_k v]]
                       (nil? v)))
             (into {}))
        x))
    m))

(defn force-rels-transactions
  "Creates a transaction that will force the relationships of the given entity to match exactly
  those in the given entity map. It does so by retracting any existing relationships
  that are not present in the new entity map, and then adding the new entity map itself.

  (By default, Datascript merges relationships - ie. additive only)

  Example:
  (force-rels-transactions
   {:resource/id 123
    :resource/outcome [{:outcome/id 1}
                       {:outcome/id 2}]})
  => [[:db/retract 123 :resource/outcome {:outcome/id 3}]
      {:resource/id 123
       :resource/outcome [{:outcome/id 1}
                          {:outcome/id 2}]}]"
  [modified-entity]
  (let [entity-type (schema/entity->entity-type modified-entity)
        id-key (schema/id-key-for entity-type)
        rel-attrs (set (schema/rel-keys-for entity-type))
        ;; we only want to update rels that are present in the modified entity
        relevant-rel-attrs (set/intersection (set (keys modified-entity))
                                             rel-attrs)
        original-entity-ref [id-key
                             (get modified-entity id-key)]
        original-entity (db/pull-ident
                         (->> (schema/schema entity-type)
                              (keep (fn [[attr opts]]
                                      (when
                                        (contains? relevant-rel-attrs attr)
                                        {attr [(schema/id-key-for (:db/rel-entity-type opts))]}))))
                         original-entity-ref)
        retractions (->> original-entity
                         (mapcat (fn [[rel-attr related]]
                                   (->> related
                                        (map (fn [v]
                                               [:db/retract
                                                original-entity-ref
                                                rel-attr
                                                ;; v is always just {:foo/id 123}
                                                ;; so can get away with using first
                                                (first v)]))))))]
    (concat retractions
            [modified-entity])))

#_(force-rels-transactions
   {:resource/id #uuid "e8f708e4-0720-4460-9e76-f912f6814918"
    :resource/outcome [{:outcome/id #uuid "694463d7-c576-4f13-b0a2-eeaa9430ed55"}
                       {:outcome/id #uuid "8944a640-5b4d-4743-9fbd-aff296f015f6"}]})

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
      (-> entity
          prep-for-transact
          ;; this endpoint assumes that if an object contains relation attributes
          ;; it will pass ALL relationships (any existing ones not included will be retracted)
          force-rels-transactions
          db/transact!)
      (db/persist!))}

   {:id :scrape!
    :params {:user-id any? ;; TODO
             :url string?} ;; TODO validate
    :conditions
    (fn [{:keys [user-id url]}]
      [[#(user-exists? user-id) :unauthorized "User not authorized"]])
    :effect
    (fn [{:keys [url]}]
      (let [scrape #_(ai/scrape! {:url url})
            (ai/mock-scrape! {:url url})
            resource-id (uuid/random)
            {:scrape/keys [description title outcomes]} scrape]
        ;; TODO get resource name and description from ai
        (db/transact! [{:resource/id resource-id
                        :resource/url url
                        :resource/outcome outcomes
                        :resource/description description
                        :resource/name title}])
        (db/persist!)
        {:resource-id resource-id}))
    :return :tada/effect-return}])

(def queries
  [{:id :data
    :params {:user-id (fn [e]
                        (or (nil? e)
                            (uuid? e)))}
    :return
    (fn [{:keys [user-id]}]
      {:db (db/->edn (db/filter-users @@db/data))
       :user (when user-id
               (assoc (db/pull-ident
                        [:user/id
                         :user/email]
                        [:user/id user-id])
                 :user/role (user-id->role user-id)))})}

   {:id :entity
    :params {:user-id (fn [e]
                        (or (nil? e)
                            (uuid? e)))
             :ident any?}
    :return
    (fn [{:keys [ident]}]
      (let [[id-attr id] ident]
        (db/q '[:find (pull ?e [*]) .
                :in $ ?id-attr ?id
                :where
                [?e ?id-attr ?id]]
              id-attr
              id)))}])

#_(tada/do! :entity {:user-id nil
                     :ident [:resource/id #uuid "589c60a6-e4ac-49c7-a961-1d3738f38a6f"]})

(tada/register! (concat commands queries))

