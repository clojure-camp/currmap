(ns clojurecamp.currmap.client.ui.badge-sidebar
  (:require
   [clojure.string :as string]
   [bloom.commons.fontawesome :as fa]
   [bloom.commons.pages :as pages]
   [reagent.core :as r]
   [clojurecamp.currmap.client.state :as state]
   [clojurecamp.currmap.client.ui.badges :as badges]
   [clojurecamp.currmap.client.ui.common :as common]
   [clojurecamp.currmap.client.ui.spreadsheet :as spreadsheet]
   [clojurecamp.currmap.domain.ratings :as ratings]))

(defn date-format [inst]
  (.toLocaleDateString inst
                       "en-CA"
                       #js {:year "numeric"
                            :month "2-digit"
                            :day "2-digit"}))

;; FAKE placeholder content — to be replaced with real schema-backed data
(def fake-description
  "This badge demonstrates a working understanding of the underlying concepts and the ability to apply them to real problems. You should be comfortable explaining the trade-offs involved and reaching for the technique when it is the right tool for the job.")

(def fake-interview-questions
  ["What is the difference between a value and an identity in this context?"
   "Walk me through how you would debug an off-by-one error here."
   "When would you reach for this technique over a simpler alternative?"])

(def fake-other-user-names
  ["Ada Lovelace" "Grace Hopper" "Alan Turing" "Katherine Johnson"])

(def fake-projects
  ["Build a small command-line to-do app that persists to a file."
   "Refactor an existing namespace to extract pure functions from side effects."])

(def fake-resources
  [{:resource/id :fake-1
    :resource/name "Official Getting Started Guide"
    :resource/url "https://example.com/guide"
    :resource/rating-values [:rating.value/strong-yes
                             :rating.value/strong-yes
                             :rating.value/weak-yes]}
   {:resource/id :fake-2
    :resource/name "Deep Dive: A Video Walkthrough"
    :resource/url "https://example.com/video"
    :resource/rating-values [:rating.value/weak-yes
                             :rating.value/weak-yes
                             :rating.value/weak-no]}
   {:resource/id :fake-3
    :resource/name "Interactive Exercises"
    :resource/url "https://example.com/exercises"
    :resource/rating-values [:rating.value/strong-yes
                             :rating.value/weak-yes]}])

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
          [common/text-button
           {:variant :ghost
            :label "Grant to other user"
            :on-click (fn [] (reset! show-user-search? true))}])))))

(defn resource-rating-view
  [{:resource/keys [name url rating-values]}]
  [:div {:tw "group relative flex items-center gap-2"}
   ;; hovering the bar graph (not the whole line) reveals the breakdown overlay
   [:div {:tw "group/bar relative w-10 h-3 shrink-0"}
    [spreadsheet/rating-view rating-values]
    ;; hover-over rating breakdown, echoing spreadsheet's rating legend
    [:div {:tw "group/bar-hover:block hidden absolute left-0 top-full z-10 w-16em p-2 bg-white border shadow"}
     (let [counts (frequencies rating-values)]
       (for [rating ratings/ratings]
         ^{:key rating}
         [:div {:tw "flex items-center gap-1 text-sm"}
          [(spreadsheet/rating->icon rating) {:tw "w-3 h-3"
                                              :style {:color (spreadsheet/rating->color rating)}}]
          [:span {:tw "grow"} (spreadsheet/rating->label rating)]
          [:span {:tw "tabular-nums text-gray-500"} (or (counts rating) 0)]]))]]
   [:a {:tw "grow underline text-sm truncate"
        :href url
        :target "_blank"
        :rel "noopener noreferrer"}
    name]
   ;; hovering the whole line reveals vote options, as in the spreadsheet popover
   [:div {:tw "inline-flex items-center gap-1 ml-auto shrink-0"}
    (for [value ratings/ratings]
      ^{:key value}
      [:div {:tw "invisible group-hover:visible"}
       [common/icon-button
        {:icon (spreadsheet/rating->icon value)
         :on-click (fn []
                     ;; TODO wire to a real :rating/resource + :rating/outcome save
                     ;; once this section is backed by real schema data
                     nil)}]])]])

(defn section-view
  [label & children]
  [:div
   [:h3 {:tw "text-xs font-bold uppercase tracking-wide text-gray-500 mb-1"}
    label]
   (into [:div] children)])

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

(defn current-badge-view
  [badge-id]
  (let [badge @(state/pull-ident
                '[:badge/id
                  :badge/name
                  :badge/level
                  :badge/description
                  {:badge/group [:badge-group/id
                                 :badge-group/name]}
                  {:badge/prerequisite [:badge/id]}]
                [:badge/id badge-id])
        badge-group (:badge/group badge)
        prerequisite-ids (->> (:badge/prerequisite badge)
                              (map :badge/id))
        ;; badges in the same group, ordered by level
        group-badges @(state/q '[:find ?b-id ?level
                                 :in $ ?group-id
                                 :where
                                 [?g :badge-group/id ?group-id]
                                 [?b :badge/group ?g]
                                 [?b :badge/id ?b-id]
                                 [?b :badge/level ?level]]
                               (:badge-group/id badge-group))
        next-in-group-id (->> group-badges
                              (sort-by second)
                              (filter (fn [[_ level]]
                                        (> level (:badge/level badge))))
                              ffirst)
        ;; badges that list this badge as a prerequisite
        leads-to-ids @(state/q '[:find [?b-id ...]
                                 :in $ ?badge-id
                                 :where
                                 [?this :badge/id ?badge-id]
                                 [?b :badge/prerequisite ?this]
                                 [?b :badge/id ?b-id]]
                               badge-id)
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
    [:div {:tw "flex flex-col"}
     ;; header: icon + group name + level
     [:div {:tw "flex items-center gap-3 p-4 text-white"
            :style {:background (badges/color (:badge-group/id badge-group))}}
      [badges/badge-icon-view {:badge badge
                               :badge-states (state/badge-states
                                              @state/user-badges-states
                                              badge-id)
                               :size 48}]
      [:div {:tw "flex flex-col"}
       [:div {:tw "text-lg font-bold"}
        (:badge-group/name badge-group)
        " "
        (badges/level->roman (:badge/level badge))]
       [:div {:tw "text-sm opacity-80"} (:badge/name badge)]]]

     ;; ribbon: if the user already has it
     (when (and @state/user (seq current-user-assertion-ids))
       [:div {:tw "flex flex-col gap-1 rounded bg-green-50 border border-green-200 p-2"}
        [:div {:tw "flex items-center gap-1 text-xs font-bold uppercase tracking-wide text-green-700"}
         [fa/fa-check-circle-solid {:tw "w-3 h-3"}]
         "You have this badge"]
        (for [assertion-id current-user-assertion-ids]
          ^{:key assertion-id}
          [assertion-view assertion-id #{:issued-by :issued-at}])])

     ;; actions
     (when @state/user
       [:div {:tw "flex flex-wrap gap-2 px-4 py-2"
              :style {:background (badges/darker (:badge-group/id badge-group))}}

        (when (empty? current-user-assertion-ids)
          [common/text-button
           {:variant :ghost
            :label (if current-user-working-towards?
                     "Remove from Working Towards"
                     "Add to Working Towards")
            :on-click (fn []
                        (state/remote-do!
                         [:api/update-working-towards-badge!
                          {:badge-id badge-id
                           :add? (not current-user-working-towards?)}]))}])
        (when (empty? current-user-assertion-ids)
          [common/text-button
           {:variant :ghost
            :label (if current-user-in-progress?
                     "Remove from In-Progress"
                     "Add to In-Progress")
            :on-click (fn []
                        (state/remote-do!
                         [:api/update-in-progress-badge!
                          {:badge-id badge-id
                           :add? (not current-user-in-progress?)}]))}])

        (when (empty? current-user-assertion-ids)
          [common/text-button
           {:variant :ghost
            :label "Grant to Self"
            :icon fa/fa-award-solid
            :on-click (fn []
                        (when (js/confirm "Are you sure you want to grant this badge to yourself?")
                          (state/remote-do!
                           [:api/grant-badge!
                            {:target-user-id (:user/id @state/user)
                             :badge-id badge-id}])))}])

        [grant-to-other-user-view badge-id]])

     [:div {:tw "space-y-4 p-4"}

      ;; description of the skill / knowledge
      (when (:badge/description badge)
        [section-view "About"
         [:div {:tw "text-sm"} (:badge/description badge)]])

      ;; description
      [section-view "Description"
       [:div {:tw "text-sm"} fake-description]]

      ;; sample interview questions
      [section-view "Sample interview questions"
       [:ul {:tw "list-disc pl-5 text-sm flex flex-col gap-1"}
        (for [question fake-interview-questions]
          ^{:key question}
          [:li question])]]

      ;; projects to prove the badge
      [section-view "Projects to prove this badge"
       [:ul {:tw "list-disc pl-5 text-sm flex flex-col gap-1"}
        (for [project fake-projects]
          ^{:key project}
          [:li project])]]

      ;; resources
      [section-view "Resources"
       ;; nested named groups aren't supported by our girouette version, create our own:
       [:style ".group\\/bar:hover .group\\/bar-hover\\:block {display:block}"]
       [:div {:tw "flex flex-col gap-1"}
        (for [resource fake-resources]
          ^{:key (:resource/id resource)}
          [resource-rating-view resource])]]

      ;; related badges
      [section-view "Related badges"
       [:div {:tw "flex flex-col gap-2"}
        (when next-in-group-id
          [:div
           [:div {:tw "text-xs text-gray-500 mb-1"} "Next in group"]
           [badges/badge-pill-view next-in-group-id]])
        (when (seq prerequisite-ids)
          [:div
           [:div {:tw "text-xs text-gray-500 mb-1"} "Prerequisites"]
           [:div {:tw "flex flex-wrap gap-1"}
            (for [prerequisite-id prerequisite-ids]
              ^{:key prerequisite-id}
              [badges/badge-pill-view prerequisite-id])]])
        (when (seq leads-to-ids)
          [:div
           [:div {:tw "text-xs text-gray-500 mb-1"} "Leads to"]
           [:div {:tw "flex flex-wrap gap-1"}
            (for [leads-to-id leads-to-ids]
              ^{:key leads-to-id}
              [badges/badge-pill-view leads-to-id])]])]]

      ;; others with the badge
      [section-view "Others with this badge"
       [:div {:tw "flex flex-col gap-1"}
        (for [user-name fake-other-user-names]
          ^{:key user-name}
          [:div {:tw "flex items-center gap-1 text-sm"}
           [common/avatar-view {:name user-name
                                :tw "w-6 h-6 text-xs"}]
           user-name])]]]]))

