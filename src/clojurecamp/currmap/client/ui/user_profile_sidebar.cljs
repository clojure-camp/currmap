(ns clojurecamp.currmap.client.ui.user-profile-sidebar
  (:require
   [clojurecamp.currmap.client.state :as state]
   [clojurecamp.currmap.client.ui.badge-sidebar :as badge-sidebar]
   [clojurecamp.currmap.client.ui.badges :as badges]
   [clojurecamp.currmap.client.ui.common :as common]))

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
                             user-id)
        granted-assertions @(state/q '[:find ?assertion-id ?issued-at
                                       :in $ ?user-id
                                       :where
                                       [?u :user/id ?user-id]
                                       [?a :assertion/issued-by ?u]
                                       [?a :assertion/user ?recipient]
                                       [(not= ?u ?recipient)]
                                       [?a :assertion/id ?assertion-id]
                                       [?a :assertion/issued-at ?issued-at]]
                                     user-id)]
    [:div {:tw "flex flex-col gap-4 p-4"}
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
         [badge-sidebar/assertion-view assertion-id #{:badge-name :issued-by :issued-at}])]]
     (when (seq granted-assertions)
       [:div
        [:h3 {:tw "text-xs font-bold uppercase tracking-wide text-gray-500 mb-1"} "Badges Granted"]
        [:div {:tw "flex flex-col gap-1"}
         (for [[assertion-id] (->> granted-assertions
                                   (sort-by second)
                                   reverse)]
           ^{:key assertion-id}
           [badge-sidebar/assertion-view assertion-id #{:badge-name :issued-to :issued-at}])]])]))
