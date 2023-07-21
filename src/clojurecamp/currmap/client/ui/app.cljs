(ns clojurecamp.currmap.client.ui.app
  (:require
    [reagent.core :as r]
    [bloom.commons.fontawesome :as fa]
    [clojurecamp.currmap.client.state :as state]
    [clojurecamp.currmap.client.ui.editor :as editor]
    [clojurecamp.currmap.client.ui.common :as ui]
    [clojurecamp.currmap.domain.ratings :as ratings]
    [clojurecamp.currmap.domain.schema :as schema]))

(def levels
  [{:level/id :level/fundamentals
    :level/name "Fundamentals"
    :level/quick-description  "\"start here\""
    :level/description "topics that provide the foundations for learning, prerequisites to later topics, \"the basics\", \"master these before moving on\""}
   {:level/id :level/core
    :level/name "Core"
    :level/quick-description "\"get to here\""
    :level/description "topics that flesh out the prerequisites for jr-devs entering their first job"}
   {:level/id :level/advanced
    :level/name "Advanced"
    :level/quick-description "\"don't worry about it\""
    :level/description "topics that can safely be deferred until much later; situational"}])

(defn tree-seq-with-depth
  "Like tree-seq, but takes in a tree of maps and adds :depth key to each map."
  [branch? children root]
  (let [walk (fn walk [depth node]
               (lazy-seq
                 (cons (assoc node :depth depth)
                       (when (branch? node)
                         (mapcat (partial walk (inc depth)) (children node))))))]
    (walk 0 root)))

(defn level-header-view [level-n]
  (let [level (get levels level-n)]
    [:th {:tw "sticky top-0 bg-white text-left border-l p-1"}
     [:div {:title (:level/description level)
            :tw "border-b"}
      [:div {:tw "font-bold flex justify-between"}
       (:level/name level)
       [fa/fa-question-circle-solid {:tw "w-4"}]]
      [:div {:tw "font-normal"}
       (:level/quick-description level)]]]))

(defn main-table-view []
  [:div
   [:table {:tw "text-sm"}
    [:thead
     [:tr
      [:th]
      [level-header-view 0]
      [level-header-view 1]
      [level-header-view 2]]]
    [:tbody
     (let [root-topic-ids @(state/q '[:find [?id ...]
                                      :where
                                      [?t :topic/id ?id]
                                      [(missing? $ ?t :topic/parent)]])
           topics (->> root-topic-ids
                       (map (fn [root-topic-id ]
                              @(state/pull-ident
                                 '[:topic/id
                                   :topic/name
                                   :topic/parent
                                   {:topic/_parent ...}
                                   {:outcome/_topic [:outcome/id
                                                     :outcome/name
                                                     :outcome/level
                                                     :outcome/type]}]
                                 [:topic/id root-topic-id])))
                       (mapcat (fn [topic]
                                 (tree-seq-with-depth map? :topic/_parent topic)))
                       doall)]
       (doall
         (for [topic topics]
           ^{:key (:topic/id topic)}
           [:<>
            (when (= 0 (:depth topic))
              [:tr {:tw "h-20 first:hidden"}])
            [:tr {:tw "even:bg-gray-200"}
             [:td {:tw "align-top p-1"
                   :style {:padding-left (str (+ 0.5 (* (:depth topic) 1)) "em")}}
              [:div.topic {:tw "group flex gap-1"}
               [:span {:tw "whitespace-nowrap font-semibold"} (:topic/name topic)]
               (when @state/admin?
                 [:div {:tw "invisible group-hover:visible"}
                  [ui/icon-button
                   {:icon fa/fa-pencil-alt-solid
                    :on-click (fn []
                                (state/open-editor!
                                  (state/entity-for-editing [:topic/id (:topic/id topic)])))}]])
               (when @state/admin?
                 [:div {:tw "invisible group-hover:visible"}
                  [ui/icon-button
                   {:icon fa/fa-plus-solid
                    :on-click (fn []
                                (state/open-editor! (merge (schema/blank :topic)
                                                           {:topic/parent {:topic/id (:topic/id topic)}})))}]])]]
             (let [outcomes-by-level (->> (:outcome/_topic topic)
                                          (group-by :outcome/level))]
               (doall
                 (for [level (map :level/id levels)
                       :let [outcomes (outcomes-by-level level)]]
                   ^{:key level}
                   [:<>
                    [:td.level {:tw "group align-top p-1 space-y-1 border-l"}
                     [:ul {:tw "list-disc ml-4"}
                      (doall
                        (for [outcome outcomes
                              :let [milestone? (= :outcome.type/milestone
                                                  (:outcome/type outcome))]]
                          ^{:key (:outcome/id outcome)}
                          [:li.outcome
                           (when milestone?
                             [fa/fa-star-solid {:tw "w-3 h-3 -mt-1 -ml-4 inline mr-1"}])
                           [:div {:tw "inline-flex gap-1 group/other"}
                            [:div {:tw ["cursor-pointer"
                                        (when milestone?
                                          "italic")
                                        (when (= (:outcome/id outcome)
                                                 (:outcome-id @state/active-outcome))
                                          "bg-yellow-200")]
                                   :on-click (fn [e]
                                               (state/set-active-outcome!
                                                 {:outcome-id (:outcome/id outcome)
                                                  :element (.. e -target)})
                                               ;; b/c we have an on-click on root to close
                                               ;; popover after every click
                                               (.stopPropagation e))}
                             (:outcome/name outcome)]
                            (when @state/admin?
                              [:div {:tw "invisible group/other-hover:visible"}
                               [ui/icon-button
                                {:icon fa/fa-pencil-alt-solid
                                 :on-click (fn [_]
                                             (state/open-editor! (state/entity-for-editing [:outcome/id (:outcome/id outcome)])))}]])]]))]
                     (when @state/admin?
                       [:div {:tw "invisible group-hover:visible"}
                        [ui/icon-button
                         {:icon fa/fa-plus-solid
                          :on-click (fn []
                                      (state/open-editor! (merge (schema/blank :outcome)
                                                                 {:outcome/level level
                                                                  :outcome/topic {:topic/id (:topic/id topic)}})))}]])]])))]])))
     [:tr
      [:td
       (when @state/admin?
         [ui/text-button
          {:label "Add a new Topic"
           :icon fa/fa-plus-solid
           :on-click (fn []
                       (state/open-editor! (schema/blank :topic)))}])]]]]])

(def color-strong-no "#ad1724")
(def color-weak-no "#df8877")
(def color-weak-yes "#7fa0ff")
(def color-strong-yes "#384ac7")

(def rating->color
  {:rating.value/strong-no color-strong-no
   :rating.value/weak-no color-weak-no
   nil "#fff"
   :rating.value/weak-yes color-weak-yes
   :rating.value/strong-yes color-strong-yes})

(def rating->icon
  {:rating.value/strong-no fa/fa-poop-solid
   :rating.value/weak-no fa/fa-thumbs-down-solid
   :rating.value/weak-yes fa/fa-thumbs-up-solid
   :rating.value/strong-yes fa/fa-star-solid})

(def rating->label
  {:rating.value/strong-no "No!"
   :rating.value/weak-no "Meh"
   :rating.value/weak-yes "It's okay"
   :rating.value/strong-yes "Yes!!!"})

(defn rating-view
  [rating-values]
  (let [ranks [:rating.value/strong-no
               :rating.value/weak-no
               :rating.value/weak-yes
               :rating.value/strong-yes]
        total (count rating-values)
        counts (->> rating-values
                    frequencies)]
    [:div {:tw "flex w-full h-full bg-gray-300"}
     (doall
       (for [rank ranks]
         (doall
           (for [i (range (counts rank))]
             ^{:key (str rank "-" i)}
             [:div {:style {:background (rating->color rank)
                            :width (str (* 100 (/ 1 total)) "%")
                            :height "100%"
                            :display "flex"
                            :align-items "center"
                            :justify-content "center"}}]))))]))

(defn outcome-popover-view []
  [:div#tooltip
   {:tw (when (nil? @state/active-outcome) "hidden")
    :on-click (fn [e]
                ;; b/c we have an on-click on root to close
                ;; popover after every click
                (.stopPropagation e))
    :ref (fn [el]
           (when el
             (.. js/Popper (createPopper
                             (:element @state/active-outcome)
                             el
                             (clj->js
                               {:placement "top"
                                :modifiers [{:name "offset"
                                             :options {:offset [0 8]}}]})))))}
   [:style
    "#tooltip[data-popper-placement^='top'] > #arrow { bottom: -0.5em; }
    #tooltip[data-popper-placement^='top'] > #arrow > div { transform: rotate(225deg); }
    #tooltip[data-popper-placement^='bottom'] > #arrow { top: -0.5em; }
    #tooltip[data-popper-placement^='bottom'] > #arrow > div { transform: rotate(45deg); }
    #tooltip[data-popper-placement^='left'] > #arrow { right: -0.5em; }
    #tooltip[data-popper-placement^='left'] > #arrow > div { transform: rotate(135deg); }
    #tooltip[data-popper-placement^='right'] > #arrow { left: -0.5em; }
    #tooltip[data-popper-placement^='right'] > #arrow > div { transform: rotate(315deg); }
    "]
   [:div#arrow {:data-popper-arrow true
                :tw "absolute"}
    [:div {:tw "w-1em h-1em bg-white transform border-t-1 border-l-1"}]]
   (when @state/active-outcome
     (let [outcome @(state/pull-ident
                      [:outcome/id
                       :outcome/name
                       :outcome/level
                       :outcome/description
                       {:resource/_outcome [:resource/id
                                            :resource/name
                                            :resource/url]}]
                      [:outcome/id (:outcome-id @state/active-outcome)])]
       [:div {:tw "bg-white shadow p-4 min-w-20em border"}
        [:div (:outcome/description outcome)]
        [:div.legend {:tw "flex"}
         [:h2 {:tw "grow"} "Resources"]
         [:span {:tw "group relative"}
          [fa/fa-question-circle-solid {:tw "w-4 h-4 text-gray-300 mr-2"}]
          [:div {:tw "hidden group-hover:block absolute right-0 w-20em p-2 bg-white border"}
           [:div "Would you recommend this resource to someone trying to learn " [:strong (:outcome/name outcome)] "?"]
           [:div
            (for [rating ratings/ratings]
              ^{:key rating}
              [:div {:tw "flex items-center gap-1"}
               [(rating->icon rating) {:tw "w-3 h-3"
                                       :style {:color (rating->color rating)}}]
               (rating->label rating)])]]]]
        (let [resources-with-rating-values
              (->> (:resource/_outcome outcome)
                   (map (fn [resource]
                          (assoc resource :resource/rating-values
                            @(state/q '[:find [?value ...]
                                        :in $ ?resource-id ?outcome-id
                                        :where
                                        [?r-eid :resource/id ?resource-id]
                                        [?o-eid :outcome/id ?outcome-id]
                                        [?r :rating/resource ?r-eid]
                                        [?r :rating/outcome ?o-eid]
                                        [?r :rating/value ?value]]
                                      (:resource/id resource)
                                      (:outcome/id outcome))))))]
          (doall
            (for [resource (ratings/sort :resource/rating-values resources-with-rating-values)]
              ^{:key (:resource/id resource)}
              [:div.resource {:tw "group flex gap-2 items-center"}
               [:div.rating {:tw "w-10 h-3"}
                [rating-view (:resource/rating-values resource)]]
               [:a {:tw "block grow underline"
                    :href (:resource/url resource)
                    :target "_blank"
                    :rel "noopener noreferrer"}
                (:resource/name resource)]
               [:div {:tw "invisible group-hover:visible"}
                [ui/icon-button
                 {:icon fa/fa-pencil-alt-solid
                  :on-click (fn [_]
                              (state/open-editor! (state/entity-for-editing [:resource/id (:resource/id resource)])))}]]
               (let [user-rating-id @(state/q '[:find ?rating-id .
                                                :in $ ?resource-id ?outcome-id ?user-id
                                                :where
                                                [?resource-eid :resource/id ?resource-id]
                                                [?outcome-eid :outcome/id ?outcome-id]
                                                [?user-eid :user/id ?user-id]
                                                [?rating-eid :rating/user ?user-eid]
                                                [?rating-eid :rating/resource ?resource-eid]
                                                [?rating-eid :rating/outcome ?outcome-eid]
                                                [?rating-eid :rating/id ?rating-id]]
                                              (:resource/id resource)
                                              (:outcome/id outcome)
                                              (:user/id @state/user))
                     user-rating @(state/pull-ident
                                    [:rating/id :rating/value]
                                    [:rating/id user-rating-id])]
                 [:div.rate {:tw "inline-flex items-center gap-1 mr-1"}
                  (for [value ratings/ratings]
                    ^{:key value}
                    [:div {:tw "invisible group-hover:visible"}
                     [ui/icon-button
                      {:icon (rating->icon value)
                       ;; girouette doesn't support !important modifier
                       :style (when (= value (:rating/value user-rating))
                                {:color (rating->color value)
                                 :visibility "visible"})
                       :on-click (fn []
                                   (state/save-entity!
                                     (merge (schema/blank :rating)
                                            user-rating ;; if there is a rating, use its :rating/id
                                            {:rating/user {:user/id (:user/id @state/user)}
                                             :rating/resource {:resource/id (:resource/id resource)}
                                             :rating/outcome {:outcome/id (:outcome/id outcome)}
                                             :rating/value value})))}]])])])))
        [ui/text-button
         {:icon fa/fa-plus-solid
          :label "Add a new Resource"
          :on-click (fn []
                      (state/open-editor!
                        (merge (schema/blank :resource)
                               {:resource/outcome [{:outcome/id (:outcome/id outcome)}]})))}]]))])

(defn entity-editor-view
  []
  (when-let [e @state/active-editor-entity]
    [editor/editor-view e]))

(defn auth-view
  []
  (if @state/user
    [ui/text-button
     {:label "Log Out"
      :on-click (fn []
                  (state/log-out!))}]
    [ui/text-button
     {:label "Log In"
      :on-click (fn []
                  (when-let [email (js/prompt "Please enter your email:")]
                    (state/authenticate! email)))}]))

(defn app-view []
  [:<>
   ;; nested groups don't work in our version of girouette, create our own:
   [:style ".group\\/other:hover .group\\/other-hover\\:visible {visibility:visible}"]
   (when @state/ready?
     [:div {:tw "w-full flex"
            :on-click (fn []
                        (state/set-active-outcome! nil))}
      [entity-editor-view]
      [:div {:tw "absolute top-0 left-0"}
       [auth-view]]
      [main-table-view]
      [:div {:tw "absolute"}
       [outcome-popover-view]]])])
