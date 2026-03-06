(ns clojurecamp.currmap.client.ui.resources
  (:require
   [bloom.commons.pages :as pages]
   [malli.core :as m]
   [clojure.string :as string]
   [clojure.walk :as walk]
   [reagent.core :as r]
   [clojurecamp.currmap.domain.schema :as schema]
   [clojurecamp.currmap.client.state :as state]
   [clojurecamp.currmap.client.ui.common :as ui]
   [clojurecamp.currmap.client.ui.editor :as editor]))

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
                 :tw ["hover:bg-yellow-300 hover:cursor-pointer"
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
   [:div {:tw "bg-yellow-100 min-w-30em"}
    [:input {:tw "border rounded p-1 w-full"
             :type "search"
             :placeholder "Search..."
             :default-value @query
             :on-change (fn [e]
                          (reset! query (.. e -target -value)))}]
    [:div
     {:tw "shrink-0 h-90vh overflow-y-auto"}
     (for [topic (filter-outcomes nested-topics @query)]
       ^{:key (:topic/id topic)}
       [topic-view topic props])]]))

(defn resource-editor-editing-view
  [{:keys [resource-id]}]
  (r/with-let
   [editor-state (r/atom {}
                         :validator (fn [value]
                                      (if-let [errors (m/explain
                                                       [:map
                                                        [:editor-state/resource-original {:optional true} :any #_(schema/malli-spec-for :resource)]
                                                        [:editor-state/resource-draft {:optional true} :any #_(schema/malli-spec-for :resource)]]
                                                       value)]
                                        (do
                                          (println errors)
                                          false)
                                        true)))
    resource-draft (r/cursor editor-state [:editor-state/resource-draft])
    _load_ (-> (state/fetch-entity!
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
                                                    (assoc :editor-state/resource-original resource)
                                                    (assoc :editor-state/resource-draft resource))))))))]
   (when (:editor-state/resource-draft @editor-state)
     [:div {:tw "flex"}
      [:div.column
       [:div {:tw "flex justify-between p-1 bg-gray-300"}
         [:h1 {:tw "font-bold"} "Editing " (:resource/id (:editor-state/resource-draft @editor-state))]
         [ui/text-button {:label "Save"
                          :on-click (fn []
                                      (-> (state/remote-do!
                                           [:upsert-entity!
                                            {:entity
                                             (->> @editor-state
                                                  :editor-state/resource-draft
                                                  schema/strip-extra-keys)}])))}]]

        [:div {:tw "p-2"}
         #_[:pre {:tw "text-xs whitespace-pre-wrap"}
         (with-out-str (cljs.pprint/pprint (:editor-state/resource-draft @editor-state)))]
        (when @resource-draft
          [editor/embeddable-editor-view {:entity resource-draft}])]]

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
                               (select-keys outcome [:outcome/id])))}]])))

(defn resource-editor-new-view
  []
  (r/with-let
   [scraping? (r/atom false)]
   (if (not @scraping?)
      [:form {:on-submit (fn [e]
                           (.preventDefault e)
                           (reset! scraping? true)
                           (-> (state/remote-do!
                                [:scrape!
                                 {:url (.-value (aget (.-elements (.-target e)) "url"))}])
                               (.then (fn [{:keys [resource-id]}]
                                        (pages/navigate-to! [:resource-editor-resource {:resource-id resource-id}])))
                               (.catch (fn []
                                         (js/alert "Error creating resource.")))))}
       [:label
        [:div "URL"]
        [:input {:placeholder "https://example.com"
                :name "url"}]]
      [:button "Scrape"]]
     ;; scraping
     [:div "Scraping..."])))

(defn resources-list-view
  []
  [:div
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
     (doall
      (for [resource resources]
        ^{:key (:resource/id resource)}
        [:a {:tw ["block"
                  (when (pages/active? [:resource-editor-resource {:resource-id (:resource/id resource)}])
                    "font-bold")]
             :href (pages/path-for [:resource-editor-resource {:resource-id (:resource/id resource)}])}
         (:resource/name resource)
         (when (:resource/url resource)
           [:span " (" (:resource/url resource) ")"])])))])

(defn resources-view
  [[page-id {:keys [resource-id]}]]
  [:div {:tw "flex"}
   [:div.column {:tw "w-1/4 shrink-0 bg-gray-100 h-95vh overflow-y-auto relative"}
    [resources-list-view]
    [:div {:tw "absolute top-0 right-0"}
     [:a {:href (pages/path-for [:resource-editor-new])}
      "Add New Resource"]]]

   (case page-id
     :resource-editor
     nil

     :resource-editor-new
     [resource-editor-new-view]

     :resource-editor-resource
     ^{:key resource-id} ;; add key need to force remount
     [resource-editor-editing-view {:resource-id resource-id}])])

(def pages
  [{:page/id :resource-editor
    :page/view #'resources-view
    :page/path "/resources-editor"}
   {:page/id :resource-editor-new
    :page/view #'resources-view
    :page/path "/resources-editor/new"}
   {:page/id :resource-editor-resource
    :page/view #'resources-view
    :page/path "/resources-editor/resource/:resource-id"
    :page/parameters {:resource-id :uuid}}])
