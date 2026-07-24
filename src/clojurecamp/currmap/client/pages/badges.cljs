(ns clojurecamp.currmap.client.pages.badges
  (:require
   [clojurecamp.currmap.client.state :as state]
   [clojurecamp.currmap.client.ui.badge-map :as bm]
   [clojurecamp.currmap.client.ui.badge-sidebar :as badge-sidebar]
   [clojurecamp.currmap.client.ui.user-profile-sidebar :as user-profile-sidebar]))

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
      [user-profile-sidebar/user-profile-badges-view user-id]}]))

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