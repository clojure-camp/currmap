(ns clojurecamp.currmap.client.pages.badges
  (:require
   [clojurecamp.currmap.client.state :as state]
   [clojurecamp.currmap.client.ui.badge-map :as bm]
   [clojurecamp.currmap.client.ui.badge-sidebar :as badge-sidebar]
   [clojurecamp.currmap.client.ui.badges :as badges]
   [clojurecamp.currmap.client.ui.common :as common]))

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
         [badge-sidebar/assertion-view assertion-id #{:badge-name :issued-by :issued-at}])]]]))

(defn badges-map-with-sidebar-view
  [{:keys [active-badge-id sidebar]}]
  [:div {:tw "flex grow min-h-0"}
   [:div {:tw "grow min-w-0"}
    [badges-map-view {:active-badge-id active-badge-id}]]
   (when sidebar
     [:div {:tw "w-30em shrink-0 bg-gray-100 p-4"}
      sidebar])])

(defn badge-page-view
  [[_ {:keys [badge-id]}]]
  [badges-map-with-sidebar-view
   {:active-badge-id badge-id
    :sidebar (when badge-id
               ^{:key badge-id}
               [badge-sidebar/current-badge-view badge-id])}])

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