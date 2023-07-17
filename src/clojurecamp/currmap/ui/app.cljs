(ns clojurecamp.currmap.ui.app
  (:require
    [reagent.core :as r]
    [bloom.commons.fontawesome :as fa]
    [clojurecamp.currmap.state :as state]
    [clojurecamp.currmap.ui.editor :as editor]
    [clojurecamp.currmap.domain.ratings :as ratings]
    [clojurecamp.currmap.schema :as schema]))

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
    :level/description "topics that can safely be deferred until much later"}])

(defn tree-seq-with-depth
  "Like tree-seq, but takes in a tree of maps and adds :depth key to each map."
  [branch? children root]
  (let [walk (fn walk [depth node]
               (lazy-seq
                 (cons (assoc node :depth depth)
                       (when (branch? node)
                         (mapcat (partial walk (inc depth)) (children node))))))]
    (walk 0 root)))

(defonce active-outcome-id (r/atom nil))

(defn level-header-view [level-n]
  (let [level (get levels level-n)]
    [:div {:title (:level/description level)}
     [:div {:tw "font-bold flex justify-between"}
      (:level/name level)
      [fa/fa-question-circle-solid {:tw "w-4"}]]
     [:div
      (:level/quick-description level)]]))

(defn main-table-view []
  [:div
   [:table
    [:thead
     [:tr
      [:td]
      [:td [level-header-view 0]]
      [:td]
      [:td [level-header-view 1]]
      [:td]
      [:td [level-header-view 2]]
      [:td]]]
    [:tbody
     (let [root-topic-ids @(state/q '[:find [?id ...]
                                      :where
                                      [?t :topic/id ?id]
                                      [(missing? $ ?t :topic/parent)]])
           topics (->> root-topic-ids
                       (map (fn [root-topic-id ]
                              @(state/pull' '[:topic/id
                                              :topic/name
                                              :topic/parent
                                              {:topic/_parent ...}
                                              {:goal/_topic [:goal/id
                                                             :goal/description
                                                             :goal/level]}
                                              {:outcome/_topic [:outcome/id
                                                                :outcome/name
                                                                :outcome/level]}]
                                            [:topic/id root-topic-id])))
                       (mapcat (fn [topic]
                                 (tree-seq-with-depth map? :topic/_parent topic)))
                       doall)]
       (doall
         (for [topic topics]
           ^{:key (:topic/id topic)}
             [:tr {:tw "even:bg-gray-200"}
              [:td {:style {:padding-left (str (* (:depth topic) 1) "em")}}
               [:div.topic {:tw "group flex gap-1"}
                [:span {:tw "whitespace-nowrap"} (:topic/name topic)]
                [:button {:tw "text-gray-600 invisible group-hover:visible"
                          :on-click (fn []
                                      (state/open-editor!
                                       @(state/pull-for-editing [:topic/id (:topic/id topic)])))}
                 [fa/fa-pencil-alt-solid {:tw "w-3 h-3"}]]
                [:button {:tw "text-gray-600 invisible group-hover:visible"
                          :on-click (fn []
                                      (state/open-editor! (merge (schema/blank :topic)
                                                                  {:topic/parent {:topic/id (:topic/id topic)}})))}
                 [fa/fa-plus-solid {:tw "w-3 h-3"}]]]]
              (let [outcomes-by-level (->> (:outcome/_topic topic)
                                           (group-by :outcome/level))
                    goals-by-level (->> (:goal/_topic topic)
                                        (group-by :goal/level))]
                (doall
                  (for [level (map :level/id levels)
                        :let [outcomes (outcomes-by-level level)
                              goal (first (goals-by-level level))]]
                    ^{:key level}
                    [:<>
                     [:td.level
                      [:ul
                       (for [outcome outcomes]
                         ^{:key (:outcome/id outcome)}
                         [:li {:tw "cursor-pointer"
                               :on-click (fn []
                                           (reset! active-outcome-id (:outcome/id outcome)))}
                          "- " (:outcome/name outcome)])]]
                     [:td.goal
                      (when goal
                        [:img {:title (:goal/description goal)
                               :src "/images/fa-portal-enter.svg"
                               :tw "w-4"}])]])))])))
     [:tr
      [:td
       [:button {:on-click (fn []
                             (state/open-editor! (schema/blank :topic)))}
        "+ New Topic"]]]]]])

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

(defn rating-view
  [rating-values]
  (let [ranks [:rating.value/strong-no
               :rating.value/weak-no
               :rating.value/weak-yes
               :rating.value/strong-yes]
        total (count rating-values)
        counts (->> rating-values
                    frequencies)]
    [:div {:tw "flex w-full h-full"}
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

(defn sidebar-view []
   (when @active-outcome-id
     (let [outcome @(state/pull' [:outcome/id
                                  :outcome/name
                                  :outcome/level
                                  {:resource/_outcome [:resource/id
                                                       :resource/name
                                                       :resource/url]}]
                                [:outcome/id @active-outcome-id])]
       [:div.outcome
        [:div "Outcome"]
        [:h1 (:outcome/name outcome)]
        [:h2 "Resources"]
        (let [resources-with-rating-values (->> (:resource/_outcome outcome)
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
              [:div.resource {:tw "flex"}
               [:a {:tw "block grow"
                    :href (:resource/url resource)
                    :target "_blank"
                    :rel "noopener noreferrer"}
                (:resource/name resource)]
               [:div.rating
                [:div {:tw "w-10"}
                 [rating-view (:resource/rating-values resource)]]]])))])))

(defn entity-editor-view
  []
  (when-let [e @state/active-editor-entity]
    [editor/editor-view e]))

(defn app-view []
  (when @state/ready?
    [:div {:tw "w-full flex"}
     [entity-editor-view]
     [:div {:tw "w-4/6"}
      [main-table-view]]
     [:div {:tw "w-2/6"}
      [sidebar-view]]]))
