(ns clojurecamp.currmap.client.state
  (:require
   [reagent.core :as r]
   [bloom.commons.ajax :as ajax]
   [clojurecamp.currmap.client.db :as db]
   [clojurecamp.currmap.domain.schema :as schema]))

(defn ajax! [params]
  (js/Promise.
   (fn [resolve reject]
     (ajax/request
      (assoc params
             :on-success (fn [data]
                           (resolve data))
             :on-error (fn [error]
                         (reject error)))))))

(defn tada!
  [[event-id event-params]]
  (js/Promise.
   (fn [resolve reject]
     (ajax/request
      {:uri (str "/api/tada/"
                 (when (namespace event-id)
                   (str (namespace event-id) "."))
                 (name event-id))
       :method :POST
       :params {:event-id event-id
                :event-params event-params}
       :on-success resolve
       :on-error reject}))))

(defn remote-do!
  [[event-id event-params]]
  (-> (tada! [event-id event-params])
      (.then (fn [data]
               (when-let [tx (:tx data)]
                 (db/transact! tx))
               data))))

;; datascript stuff

(defonce ready? (r/reaction @db/ready?))

(def pull-ident db/rx-pull-ident)
(def q db/rx-q)

;; misc ui stuff, regular reagent atoms

(defonce state
  (r/atom
   {:db/active-editor-entity nil
    :db/user nil
    :db/active-outcome nil}))

(def active-editor-entity (r/cursor state [:db/active-editor-entity]))
(def user (r/cursor state [:db/user]))
(def active-outcome (r/cursor state [:db/active-outcome]))
(def admin? (r/reaction (= (:user/role @user)
                           :role/admin)))

(defn set-active-outcome!
  [o]
  (swap! state assoc :db/active-outcome o))

(defn authenticate!
  [email]
  (-> (remote-do!
       [:request-auth!
        {:email email}])
      (.then (fn []
               (js/alert "A log in link has been sent. Check your email.")))
      (.catch (fn []
                (js/alert "Auth error.")))))

(defn log-out!
  []
  (ajax/request
   {:uri "/api/session"
    :method :delete
    :on-success (fn []
                  (swap! state assoc :db/user nil))
    :on-error (fn []
                (js/alert "Auth error."))}))

(defn entity-for-editing
  [[id-attr _id :as ident]]
  (let [entity-type (schema/attr->entity-type id-attr)]
    (merge
     (schema/blank entity-type)
     (db/pull-ident
      (schema/pattern-for (schema/attr->entity-type id-attr))
      ident))))

(defn open-editor!
  [starter-entity]
  (swap! state assoc :db/active-editor-entity starter-entity))

(defn close-editor!
  []
  (swap! state assoc :db/active-editor-entity nil))

(defn remove-nil-values [m]
  (->> m
       (filter (fn [[_k v]]
                 v))
       (into {})))

(defn add-entity!
  [entity]
  (db/transact! [(remove-nil-values entity)]))

(defn transact!
  [tx-data]
  (-> (remote-do!
       [:transact!
        {:txs tx-data}])
      (.then (fn []
               (db/transact! tx-data)))))

(defn save-entity!
  [entity]
  (-> (remote-do!
       [:upsert-entity!
        {:entity entity}])
      (.then (fn []
               (db/transact! [(remove-nil-values entity)])))))

(defn fetch-entity!
  [ident]
  (-> (remote-do!
       [:entity
        {:ident ident}])
      (.then (fn [v]
               (add-entity! v)
               v))))

(defonce _
  (do
    (-> (remote-do!
         [:data {}])
        (.then (fn [{:keys [db user]}]
                 (db/initialize-db! db)
                 (when user
                   (swap! state assoc :db/user user)))))
    nil))
