(ns clojurecamp.currmap.ui.editor
  (:require
    [reagent.core :as r]
    [clojurecamp.currmap.state :as state]
    [clojurecamp.currmap.schema :as schema]))

(defmulti input-view #(get-in % [:schema :db/input]))

(defmethod input-view :input/text
  [{:keys [schema value on-change]}]
  [:input
   {:tw "border border-gray-500 p-1"
    :default-value value
    :on-change (fn [e]
                 (on-change (.. e -target -value)))}])

(defmethod input-view :input/rel
  [{:keys [schema value on-change]}]
  ;; for a rel, value is expected to be, for example {:topic/id #uuid "..."}
  (let [id-key (schema/id-key-for (:db/rel-entity-type schema))
        name-key (schema/name-key-for (:db/rel-entity-type schema))
        options (->> @(state/q '[:find ?id ?name
                                 :in $ ?id-key ?name-key
                                 :where
                                 [?e ?id-key ?id]
                                 [?e ?name-key ?name]]
                               id-key name-key)
                     (map (fn [[id label]]
                            [{id-key id} label])))
        ;; going through this hashing hoop to effectively allow for object values
        ;; b/c :value on option gets cast to string
        str-hash (fn [x] (str (hash x)))
        hash->value (zipmap (map str-hash (map first options))
                            (map first options))]
    [:select {:tw "border border-gray-500 p-1"
              :default-value (case (:db/cardinality schema)
                               :db.cardinality/one
                               (str-hash value)
                               :db.cardinality/many
                               (map str-hash value))
              :on-change (fn [e]
                           (case (:db/cardinality schema)
                             :db.cardinality/one
                             (on-change (hash->value (.. e -target -value)))
                             :db.cardinality/many
                             (js/alert "TODO")))}
     [:option {:value "nil"} ""]
     (for [[value label] options]
       ^{:key (str-hash value)}
       [:option {:value (str-hash value)} label])]))

(defmethod input-view :default
  [{:keys [schema value]}]
  [:div {:tw "p-1"}
   (pr-str value)])

(defn editor-view
  [[entity-type entity-id]]
  (r/with-let [entity (r/atom (if (= entity-id :new)
                                (schema/blank entity-type)
                                @(state/pull'
                                   (schema/pattern-for entity-type)
                                   [(schema/id-key-for entity-type)
                                    entity-id])))]
    [:div.wrapper {:tw "absolute p-10 inset-1/4"}
     [:div.editor {:tw "bg-white border w-full h-full"}
      #_(pr-str @entity)
      [:form {:tw "flex flex-col h-full"
              :on-submit (fn [e]
                           (.preventDefault e)
                           (state/save-entity! @entity)
                           (state/close-editor!))}
       [:table
        [:tbody
         (for [[k v] @entity]
           ^{:key k}
           [:tr
            [:td {:tw "p-1"} (pr-str k)]
            [:td [input-view
                  {:schema (get-in schema/schema [entity-type k])
                   :value v
                   :on-change (fn [new-value]
                                (swap! entity assoc k new-value))}]]])]]
       [:div.gap {:tw "grow"}]
       [:div {:tw "flex justify-between"}
        [:button {:tw "bg-gray-200 py-1 px-2"
                  :type "button"
                  :on-click (fn []
                              (state/close-editor!))}
         "Cancel"]
        [:button {:tw "bg-blue-500 text-white p-1 px-2"} "Save"]]]]]))
