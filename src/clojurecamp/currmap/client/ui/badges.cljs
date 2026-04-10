(ns clojurecamp.currmap.client.ui.badges
  (:require
   [clojure.string :as string]
   [bloom.commons.pages :as pages]
   [reagent.core :as r]
   [clojurecamp.currmap.client.tada :as tada]
   [clojurecamp.currmap.client.state :as state]
   [clojurecamp.currmap.client.ui.badge-map :as bm]))

(defn badge-view
  [badge-id]
  (let [badge @(state/pull-ident
                '[:badge/id
                  :badge/name]
                [:badge/id badge-id])]
    [:a {:href (pages/path-for [:badge {:badge-id badge-id}])}
     (:badge/name badge)]))

(defn date-format [inst]
  (.toLocaleDateString inst
                       "en-CA"
                       #js {:year "numeric"
                            :month "2-digit"
                            :day "2-digit"}))

(defn assertion-view
  [assertion-id show-attrs]
  (let [assertion @(state/pull-ident
                    '[:assertion/id
                      {:assertion/badge [:badge/id]}
                      {:assertion/issued-by [:user/id
                                             :user/name]}
                      :assertion/issued-at]
                    [:assertion/id assertion-id])]
    [:div
     (when (contains? show-attrs :badge-name)
       [badge-view (:badge/id (:assertion/badge assertion))])
     " "
     (when (contains? show-attrs :issued-by)
       (let [granting-user (:assertion/issued-by assertion)]
         (if (= (:user/id granting-user)
                (:user/id @state/user))
           "(Self-Granted)"
           (str "(Granted by " (:user/name granting-user) ")"))))
     " "
     (when (contains? show-attrs :issued-at)
       (date-format (:assertion/issued-at assertion)))]))

(defn badges-map-view
  [{:keys [active-badge-id]}]
  (let [badge-ids @(state/q '[:find [?id ...]
                              :where
                              [?t :badge/id ?id]])
        badges (->> badge-ids
                    (map (fn [badge-id]
                           @(state/pull-ident
                             '[:badge/id
                               :badge/name
                               :badge/level
                               {:badge/group [:badge-group/id
                                              :badge-group/name]}
                               {:badge/prerequisite [:badge/id]}]
                             [:badge/id badge-id])))
                    doall)]
    [bm/layout-view {:badges badges
                     :active-badge-id active-badge-id}]))

(defn grant-to-other-user-view
  [badge-id]
  (r/with-let
    [show-user-search? (r/atom false)
     results (r/atom nil)]
    (if @show-user-search?
      [:div
       [:input {:type "search"
                :autofocus true
                :placeholder "Search for user by name"
                :on-change (fn [e]
                             (reset! results (->> @(state/q '[:find ?user-id ?name
                                                              :in $ ?current-user-id
                                                              :where
                                                              [?u :user/id ?user-id]
                                                              [?u :user/name ?name]
                                                              [(not= ?user-id ?current-user-id)]]
                                                            (:user/id @state/user))
                                                  (filter (fn [[_ user-name]]
                                                            (string/includes?
                                                             (string/lower-case user-name)
                                                             (string/lower-case (.. e -target -value))))))))}]
       (when @results
         [:div
          (for [[user-id user-name] @results]
            ^{:key user-id}
            [:div {:on-click (fn []
                               (when (js/confirm (str "Are you sure you want to grant this badge to " user-name "?"))
                                 (-> (tada/tada!
                                      [:api/grant-badge!
                                       {:target-user-id user-id
                                        :badge-id badge-id}])
                                     (.then
                                      (fn []
                                        (js/alert "Badge granted successfully!"))))))}
             user-name])])]
      (let [assertion-from-third-party? @(state/q '[:find ?u .
                                                    :in $ ?user-id ?badge-id
                                                    :where
                                                    [?u :user/id ?user-id]
                                                    [?b :badge/id ?badge-id]
                                                    [?a :assertion/user ?u]
                                                    [?a :assertion/badge ?b]
                                                    [?a :assertion/issued-by ?issuer]
                                                    [(not= ?issuer ?user-id)]]
                                                  (:user/id @state/user)
                                                  badge-id)]
        (when assertion-from-third-party?
          [:button {:on-click (fn [] (reset! show-user-search? true))}
           "[GRANT TO OTHER USER]"])))))

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
        current-user-assertion-ids @(state/q '[:find [?a-id ...]
                                               :in $ ?user-id ?badge-id
                                               :where
                                               [?u :user/id ?user-id]
                                               [?b :badge/id ?badge-id]
                                               [?a :assertion/user ?u]
                                               [?a :assertion/badge ?b]
                                               [?a :assertion/id ?a-id]
                                               [?a :assertion/issued-by ?issuer]]
                                             (:user/id @state/user)
                                             badge-id)]
    [:div
     [:div (:badge/name badge)]
     (when @state/user
       [:div.user-badges
        (if (seq current-user-assertion-ids)
          [:div
           (for [assertion-id current-user-assertion-ids]
             ^{:key assertion-id}
             [assertion-view assertion-id #{:issued-by :issued-at}])]
          [:button {:on-click (fn []
                                (state/remote-do!
                                 [:api/grant-badge!
                                  {:target-user-id (:user/id @state/user)
                                   :badge-id badge-id}]))}
           "[GRANT TO SELF!]"])

        [grant-to-other-user-view badge-id]

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
        [assertion-view assertion-id #{:badge-name :issued-by :issued-at}])]

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
     [badges-map-view {:active-badge-id badge-id}]]
    (when badge-id
      [:div {:tw "w-25% bg-gray-100 p-2"}
       ^{:key badge-id}
       [current-badge-view badge-id]])]])

(def pages
  [{:page/id :badges
    :page/view #'badge-page-view
    :page/path "/badges"}
   {:page/id :badge
    :page/view #'badge-page-view
    :page/path "/badges/:badge-id"
    :page/parameters {:badge-id :uuid}}])
