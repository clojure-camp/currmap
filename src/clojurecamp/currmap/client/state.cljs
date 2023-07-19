(ns clojurecamp.currmap.client.state
  (:require
    [reagent.core :as r]
    [bloom.commons.ajax :as ajax]
    [bloom.commons.tada.rpc.client :as tada.rpc]
    [clojurecamp.currmap.client.db :as db]
    [clojurecamp.currmap.domain.schema :as schema]))

(def remote-do!
  (tada.rpc/make-dispatch {:base-path "/api/tada"}))

;; datascript stuff

(defonce ready? (r/reaction @db/ready?))

(def pull-ident db/pull-ident)
(def q db/q)

;; misc ui stuff, regular reagent atoms

(defonce state
  (r/atom
    {:db/active-editor-entity nil
     :db/user nil}))

(defn authenticate!
  [email]
  (remote-do!
    [:request-auth!
     {:email email}
     {:on-success (fn []
                    (js/alert "A log in link has been sent. Check your email."))
      :on-error (fn []
                  (js/alert "Auth error."))}]))

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
      @(pull-ident
         (schema/pattern-for (schema/attr->entity-type id-attr))
         ident))))

(defn open-editor!
  [starter-entity]
  (swap! state assoc :db/active-editor-entity starter-entity))

(defn close-editor!
  []
  (swap! state assoc :db/active-editor-entity nil))

(def active-editor-entity (r/cursor state [:db/active-editor-entity]))
(def user (r/cursor state [:db/user]))

(defn remove-nil-values [m]
  (->> m
       (filter (fn [[_k v]]
                 v))
       (into {})))

(defn save-entity!
  [entity]
  (remote-do!
    [:upsert-entity!
     {:entity entity}
     {:on-success
      (fn []
        (db/transact! [(remove-nil-values entity)]))}]))

(defonce _
  (do
    (remote-do!
      [:data
       {}
       {:on-success (fn [{:keys [db user-id]}]
                      (db/initialize-db! db)
                      (when user-id
                        (swap! state assoc :db/user {:user/id user-id})))}])
    nil))
