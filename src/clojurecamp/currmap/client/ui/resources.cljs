(ns clojurecamp.currmap.client.ui.resources
  (:require
   [malli.core :as m]
   [clojure.string :as string]
   [clojure.walk :as walk]
   [reagent.core :as r]
   [clojurecamp.currmap.domain.schema :as schema]
   [clojurecamp.currmap.client.state :as state]
   [clojurecamp.currmap.client.ui.editor :as editor]))

(defonce editor-state
  (r/atom
   {:editor-state/stage :editor-state.stage/nothing}
   :validator (fn [value]
                (if-let [errors (m/explain
                                 [:map
                                  [:editor-state/stage [:enum
                                                        :editor-state.stage/nothing
                                                        :editor-state.stage/start
                                                        :editor-state.stage/scraping
                                                        :editor-state.stage/editing]]
                                  [:editor-state/resource-original {:optional true} :any #_(schema/malli-spec-for :resource)]
                                  [:editor-state/resource-draft {:optional true} :any #_(schema/malli-spec-for :resource)]]
                                 value)]
                  (do
                    (println errors)
                    false)
                  true))))

;; not-editing
;; querying
;; editing

#_(defn resource-editor-view
  [resource-id]
  [editor/editor-view
   (state/entity-for-editing [:resource/id resource-id])])

(defn level->sort-index [level]
  (case level
    :level/fundamentals 0
    :level/core 1
    :level/advanced 2
    3))

(defn topic-view
  [topic {:keys [selected-outcome-ids remove-outcome! add-outcome!] :as props}]
  [:div
   [:div {:tw "font-bold"} (:topic/name topic)]
   (doall
    (for [[level outcomes] (->> (:outcome/_topic topic)
                                (group-by :outcome/level)
                                (sort-by (fn [[level _]]
                                           (level->sort-index level))))]
      ^{:key level}
      [:div {:tw "ml-4"}
       [:div {:tw "font-bold uppercase text-xs"} (name level)]
       (doall
        (for [outcome outcomes
              :let [selected? (contains? selected-outcome-ids (:outcome/id outcome))]]
          ^{:key (:outcome/id outcome)}
          [:div {:on-click (fn []
                             (if selected?
                               (remove-outcome! outcome)
                               (add-outcome! outcome)))
                 :tw ["hover:bg-red-500 hover:cursor-pointer"
                      (when selected?
                        "bg-green-300")]}
           (:outcome/name outcome)]))]))
   [:div {:tw "ml-4"}
    (for [topic (:topic/_parent topic)]
      ^{:key (:topic/id topic)}
      [topic-view topic props])]])

(defn filter-outcomes
  [nested-topics query]
  (let [keep-outcome? (fn [outcome]
                        (string/includes? (string/lower-case (:outcome/name outcome))
                                          (string/lower-case query)))]
    (->> (walk/postwalk (fn [node]
                          (cond
                            (and (map? node)
                                 (:outcome/id node))
                            (if (keep-outcome? node)
                              node
                              nil)

                            (and (map? node)
                                 (:topic/id node))
                            (let [topic node]
                              (if (and (empty? (:outcome/_topic topic))
                                       (empty? (:topic/_parent topic)))
                                nil
                                topic))

                            (vector? node)
                            (vec (remove nil? node))

                            (list? node)
                            (remove nil? node)

                            :else
                            node))
                        nested-topics)
         (remove nil?))))

(defn outcome-picker-view
  [props]
  (r/with-let
   [root-topic-ids @(state/q '[:find [?id ...]
                               :where
                               [?t :topic/id ?id]
                               [(missing? $ ?t :topic/parent)]])
    nested-topics (->> root-topic-ids
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
                       doall)
    query (r/atom "")]
   [:div {:tw "bg-yellow-100"}
    [:input {:tw "border rounded p-1"
             :type "search"
             :default-value @query
             :on-change (fn [e]
                          (reset! query (.. e -target -value)))}]
    (for [topic (filter-outcomes nested-topics @query)]
      ^{:key (:topic/id topic)}
      [topic-view topic props])]))

(defn start-editing! [resource-id]
  (-> (state/fetch-entity!
       [:resource/id resource-id])
      (.then (fn [_]
               (let [resource @(state/pull-ident
                                  '[:resource/id
                                    :resource/name
                                    :resource/url
                                    :resource/description
                                    {:resource/outcome [:outcome/id
                                                        :outcome/name]}]
                                  [:resource/id resource-id])]
                   (swap! editor-state (fn [state]
                                         (-> state
                                             (assoc :editor-state/stage :editor-state.stage/editing)
                                             (assoc :editor-state/resource-original resource)
                                             (assoc :editor-state/resource-draft resource)))))))))

(defn resource-editor-view
  []
  (case (:editor-state/stage @editor-state)
    :editor-state.stage/nothing
    nil

    :editor-state.stage/start
    [:form {:on-submit (fn [e]
                         (.preventDefault e)
                         (swap! editor-state assoc :editor-state/stage :editor-state.stage/scraping)
                         (state/remote-do!
                          [:scrape!
                           {:url (.-value (aget (.-elements (.-target e)) "url"))}
                           {:on-success
                            (fn [{:keys [resource-id]}]
                              (start-editing! resource-id))
                            :on-error (fn []
                                        (js/alert "Error creating resource."))}]))}
     [:label
      [:div "URL"]
      [:input {:placeholder "https://example.com"
               :name "url"}]]
     [:button "Scrape"]]

    :editor-state.stage/scraping
    [:div "Scraping..."]

    :editor-state.stage/editing
    [:div
     [:div
      [:h1 {:tw "font-bold"} "Resource Editor"]
      [:button {:on-click (fn []
                            (state/remote-do!
                             [:upsert-entity!
                              {:entity
                               (->> @editor-state
                                    :editor-state/resource-draft
                                    schema/strip-extra-keys)}]))}
       "Save"]]
     [:div {:tw "flex"}

      [:div
       (pr-str (:editor-state/resource-draft @editor-state))]
      [outcome-picker-view
       {:selected-outcome-ids (->> @editor-state
                                   :editor-state/resource-draft
                                   :resource/outcome
                                   (map :outcome/id)
                                   set)
        :remove-outcome! (fn [outcome]
                           (swap! editor-state update-in
                                  [:editor-state/resource-draft :resource/outcome]
                                  (fn [outcomes]
                                    (remove #(= (:outcome/id %) (:outcome/id outcome)) outcomes))))
        :add-outcome! (fn [outcome]
                        (swap! editor-state update-in
                               [:editor-state/resource-draft :resource/outcome]
                               conj
                               ;; backend only wants the :outcome/id to store the relation
                               #_outcome
                               (select-keys outcome [:outcome/id])))}]]]))

(defn resources-view
  []
  [:div
   [:h1 "Resources"]
   [:button {:on-click (fn []
                         (swap! editor-state assoc :editor-state/stage :editor-state.stage/start))}
    "Add New Resource"]
   (let [resource-ids @(state/q '[:find [?id ...]
                                  :where
                                  [?e :resource/id ?id]])
         resources (->> resource-ids
                        (map (fn [id]
                               @(state/pull-ident
                                 '[:resource/id
                                   :resource/name
                                   :resource/url]
                                 [:resource/id id])))
                        doall)]
     (for [resource resources]
       ^{:key (:resource/id resource)}
       [:div {:tw "cursor-pointer"
              :on-click (fn []
                          (start-editing! (:resource/id resource)))}
        [:div {:tw "font-bold"} (:resource/name resource)]
        [:div {}
         (:resource/title resource)
         "(" (:resource/url resource) ")"]]))
   [resource-editor-view]])


