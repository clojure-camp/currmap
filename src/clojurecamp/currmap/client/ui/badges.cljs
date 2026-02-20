(ns clojurecamp.currmap.client.ui.badges
  (:require
   [bloom.commons.pages :as pages]
   [reagent.core :as r]
   [clojurecamp.currmap.client.state :as state]
   [clojurecamp.currmap.client.ui.badge-map :as bm]))

(defn badges-map-view []
  (let [badge-ids @(state/q '[:find [?id ...]
                              :where
                              [?t :badge/id ?id]])
        badges (->> badge-ids
                    (map (fn [badge-id]
                           @(state/pull-ident
                             '[:badge/id
                               :badge/name
                               {:badge/prerequisite [:badge/id]}]
                             [:badge/id badge-id])))
                    doall)]
    [bm/layout-view badges]))

(defn current-badge-view
  [badge-id]
  (let [badge @(state/pull-ident
                '[:badge/id
                  :badge/name
                  :badge/description]
                [:badge/id badge-id])
        current-user-working-towards? (boolean @(state/q '[:find ?u .
                                                           :in $ ?user-id ?badge-id
                                                           :where
                                                           [?u :user/id ?user-id]
                                                           [?b :badge/id ?badge-id]
                                                           [?u :user/badge-working-towards ?b]]
                                                         (:user/id @state/user)
                                                         badge-id))
        current-user-in-progress? (boolean @(state/q '[:find ?u .
                                                       :in $ ?user-id ?badge-id
                                                       :where
                                                       [?u :user/id ?user-id]
                                                       [?b :badge/id ?badge-id]
                                                       [?u :user/badge-in-progress ?b]]
                                                     (:user/id @state/user)
                                                     badge-id))
        current-user-assertion-id-pairs @(state/q '[:find ?a-id ?issuer-id
                                                    :in $ ?user-id ?badge-id
                                                    :where
                                                    [?u :user/id ?user-id]
                                                    [?b :badge/id ?badge-id]
                                                    [?a :assertion/user ?u]
                                                    [?a :assertion/badge ?b]
                                                    [?a :assertion/id ?a-id]
                                                    [?a :assertion/issued-by ?issuer]
                                                    [?issuer :user/id ?issuer-id]]
                                                  (:user/id @state/user)
                                                  badge-id)]
    [:div
     [:div (:badge/name badge)]
     (when @state/user
       [:div.user-badges
        (if-let [_self-granted? (->> current-user-assertion-id-pairs
                                     (some (fn [[_ issuer-id]]
                                             (= issuer-id
                                                (:user/id @state/user)))))]
          [:div
           "(Self-Granted)"]
          [:button {:on-click (fn []
                                (state/save-entity!
                                 {:assertion/id (random-uuid)
                                  :assertion/badge {:badge/id badge-id}
                                  :assertion/user {:user/id (:user/id @state/user)}
                                  :assertion/issued-by
                                  {:user/id (:user/id @state/user)}
                                  :assertion/issued-at (js/Date.)}))}
           "GRANT TO SELF!"])

        [:button {:on-click (fn []
                              (state/transact!
                               [[(if current-user-working-towards?
                                   :db/retract
                                   :db/add)
                                 [:user/id (:user/id @state/user)]
                                 :user/badge-working-towards
                                 [:badge/id badge-id]]]))}
         (if current-user-working-towards?
           "Remove from working towards"
           "Add to working towards")]
        [:button {:on-click (fn []
                              (state/transact!
                               [[(if current-user-in-progress?
                                   :db/retract
                                   :db/add)
                                 [:user/id (:user/id @state/user)]
                                 :user/badge-in-progress
                                 [:badge/id badge-id]]]))}
         (if current-user-in-progress?
           "Remove from in-progress"
           "Add to in-progress")]])]))

(defn badge-view
  [badge-id]
  (let [badge @(state/pull-ident
                '[:badge/id
                  :badge/name]
                [:badge/id badge-id])]
    [:span
     (:badge/name badge)]))

(defn date-format [inst]
  (.toLocaleDateString inst
                       "en-CA"
                       #js {:year "numeric"
                            :month "2-digit"
                            :day "2-digit"}))

(defn assertion-view
  [assertion-id]
  (let [assertion @(state/pull-ident
                    '[:assertion/id
                      {:assertion/badge [:badge/id]}
                      {:assertion/issued-by [:user/id
                                             :user/name]}
                      :assertion/issued-at]
                    [:assertion/id assertion-id])]
    [:div
     [badge-view (:badge/id (:assertion/badge assertion))]
     " "
     (let [granting-user (:assertion/issued-by assertion)]
       (if (= (:user/id granting-user)
              (:user/id @state/user))
         "(Self-Granted)"
         (str "(Granted by " (:user/name granting-user) ")")))
     " "
     (date-format (:assertion/issued-at assertion))]))

(defn user-profile-badges-view
  []
  (let [user-id (:user/id @state/user)
        working-towards-badges @(state/q '[:find [?b-id ...]
                                           :in $ ?user-id
                                           :where
                                           [?u :user/id ?user-id]
                                           [?u :user/badge-working-towards ?b]
                                           [?b :badge/id ?b-id]]
                                         user-id)
        in-progress-badges @(state/q '[:find [?b-id ...]
                                       :in $ ?user-id
                                       :where
                                       [?u :user/id ?user-id]
                                       [?u :user/badge-in-progress ?b]
                                       [?b :badge/id ?b-id]]
                                     user-id)
        assertions @(state/q '[:find [?assertion-id ...]
                               :in $ ?user-id
                               :where
                               [?u :user/id ?user-id]
                               [?a :assertion/user ?u]
                               [?a :assertion/id ?assertion-id]]
                               user-id)]
    [:div
      [:div
       [:h3 {:tw "font-bold"} "Badges"]
       (for [assertion-id assertions]
         ^{:key assertion-id}
         [assertion-view assertion-id])]

     [:div
      [:h3 {:tw "font-bold"} "Working Towards"]
      (for [badge-id working-towards-badges]
        ^{:key badge-id}
        [badge-view badge-id])]
     [:div
      [:h3 {:tw "font-bold"} "In Progress"]
      (for [badge-id in-progress-badges]
        ^{:key badge-id}
        [badge-view badge-id])]]))

(defn badge-page-view
  [[_ {:keys [badge-id]}]]
  [:div
   [user-profile-badges-view]

   [:br]
   [:br]
   [:br]
   [:br]
   [:br]
   [:br]
   [:div {:tw "flex grow"}
    [:div {:tw "w-75% overflow-x-auto"}
     [badges-map-view]]
    (when badge-id
      [:div {:tw "w-25% bg-gray-100 p-2"}
       [current-badge-view badge-id]])]])

(def pages
  [{:page/id :badges
    :page/view #'badge-page-view
    :page/path "/badges"}
   {:page/id :badge
    :page/view #'badge-page-view
    :page/path "/badges/:badge-id"
    :page/parameters {:badge-id :uuid}}])
