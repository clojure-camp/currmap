(ns clojurecamp.currmap.client.pages.badges
  (:require
   [clojure.string :as string]
   [bloom.commons.pages :as pages]
   [reagent.core :as r]
   [clojurecamp.currmap.client.state :as state]
   [clojurecamp.currmap.client.ui.badge-map :as bm]
   [clojurecamp.currmap.client.ui.badges :as badges]
   [clojurecamp.currmap.client.ui.common :as common]))

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
                      {:assertion/user [:user/id]}
                      {:assertion/issued-by [:user/id
                                             :user/name]}
                      :assertion/issued-at]
                    [:assertion/id assertion-id])
        granting-user (:assertion/issued-by assertion)
        self-granted? (= (:user/id granting-user)
                         (:user/id (:assertion/user assertion)))]
    [:div {:tw "flex items-center justify-between gap-2"}
     (when (contains? show-attrs :badge-name)
       [badges/badge-pill-view (:badge/id (:assertion/badge assertion))])
     [:div {:tw "flex items-center gap-2 ml-auto"}
      (when (and (contains? show-attrs :issued-by)
                 (not self-granted?))
        [:a {:href (pages/path-for [:user-profile {:user-id (:user/id granting-user)}])}
         [common/avatar-view {:name (:user/name granting-user)
                              :tw "w-5 h-5 text-xs"}]])
      (when (contains? show-attrs :issued-at)
        [:div {:tw "text-sm text-gray-500 tabular-nums"}
         (date-format (:assertion/issued-at assertion))])]]))

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
                                 (-> (state/remote-do!
                                      [:api/grant-badge!
                                       {:target-user-id user-id
                                        :badge-id badge-id}])
                                     (.then (fn [_] (js/alert "Badge granted successfully!"))))))}
             user-name])])]
      (let [assertion-from-third-party? @(state/q '[:find ?u .
                                                    :in $ ?user-id ?badge-id
                                                    :where
                                                    [?u :user/id ?user-id]
                                                    [?b :badge/id ?badge-id]
                                                    [?a :assertion/user ?u]
                                                    [?a :assertion/badge ?b]
                                                    [?a :assertion/issued-by ?issuer]
                                                    [(not= ?issuer ?u)]]
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
                              (state/remote-do!
                               [:api/update-working-towards-badge!
                                {:badge-id badge-id
                                 :add? (not current-user-working-towards?)}]))}
         (if current-user-working-towards?
           "Remove from working towards"
           "Add to working towards")]
        [:button {:on-click (fn []
                              (state/remote-do!
                               [:api/update-in-progress-badge!
                                {:badge-id badge-id
                                 :add? (not current-user-in-progress?)}]))}
         (if current-user-in-progress?
           "Remove from in-progress"
           "Add to in-progress")]])]))

(defn profile-header-view
  [user-id]
  (let [user @(state/pull-ident '[:user/id :user/name] [:user/id user-id])]
    [:div {:tw "flex items-center gap-3 pb-4 border-b border-gray-200"}
     [common/avatar-view {:name (:user/name user)}]
     [:div {:tw "flex flex-col gap-2"}
      [:div {:tw "text-lg font-bold"} (:user/name user)]
      ;; links placeholder
      [:div {:tw "flex justify-start gap-3 text-sm text-gray-400"}
       (for [label ["GitHub" "Website" "LinkedIn"]]
         ^{:key label}
         [:a {:tw "bg-gray-100"} label])]]]))

(defn user-profile-badges-view
  [user-id]
  (let [working-towards-badges @(state/q '[:find [?b-id ...]
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
        assertions @(state/q '[:find ?assertion-id ?issued-at
                               :in $ ?user-id
                               :where
                               [?u :user/id ?user-id]
                               [?a :assertion/user ?u]
                               [?a :assertion/id ?assertion-id]
                               [?a :assertion/issued-at ?issued-at]]
                             user-id)]
    [:div {:tw "flex flex-col gap-4"}
     [profile-header-view user-id]
     [:div
      [:h3 {:tw "text-xs font-bold uppercase tracking-wide text-gray-500 mb-1"} "In Progress"]
      [:div {:tw "flex flex-wrap gap-1"}
       (for [badge-id in-progress-badges]
         ^{:key badge-id}
         [badges/badge-pill-view badge-id])]]
     [:div
      [:h3 {:tw "text-xs font-bold uppercase tracking-wide text-gray-500 mb-1"} "Working Towards"]
      [:div {:tw "flex flex-wrap gap-1"}
       (for [badge-id working-towards-badges]
         ^{:key badge-id}
         [badges/badge-pill-view badge-id])]]
     [:div
      [:h3 {:tw "text-xs font-bold uppercase tracking-wide text-gray-500 mb-1"} "Badges"]
      [:div {:tw "flex flex-col gap-1"}
       (for [[assertion-id] (->> assertions
                                 (sort-by second)
                                 reverse)]
         ^{:key assertion-id}
         [assertion-view assertion-id #{:badge-name :issued-by :issued-at}])]]]))

(defn badges-map-with-sidebar-view
  [{:keys [active-badge-id sidebar]}]
  [:div {:tw "flex grow"}
   [:div {:tw "w-75% overflow-x-auto"}
    [badges-map-view {:active-badge-id active-badge-id}]]
   (when sidebar
     [:div {:tw "w-25% bg-gray-100 p-4"}
      sidebar])])

(defn badge-page-view
  [[_ {:keys [badge-id]}]]
  [badges-map-with-sidebar-view
   {:active-badge-id badge-id
    :sidebar (when badge-id
               ^{:key badge-id}
               [current-badge-view badge-id])}])

(defn badges-profile-page-view
  [[_ {:keys [user-id]}]]
  (let [user-id (or user-id (:user/id @state/user))]
    [badges-map-with-sidebar-view
     {:sidebar ^{:key user-id}
      [user-profile-badges-view user-id]}]))

(def pages
  [{:page/id :badges
    :page/view #'badge-page-view
    :page/path "/badges"}
   {:page/id :badge
    :page/view #'badge-page-view
    :page/path "/badges/:badge-id"
    :page/parameters {:badge-id :uuid}}
   {:page/id :badges-profile
    :page/view #'badges-profile-page-view
    :page/path "/badges-profile"}
   {:page/id :user-profile
    :page/view #'badges-profile-page-view
    :page/path "/badges-profile/:user-id"
    :page/parameters {:user-id :uuid}}])
