(ns clojurecamp.currmap.client.ui.editor
  (:require
    [reagent.core :as r]
    [bloom.commons.fontawesome :as fa]
    [malli.core :as m]
    [malli.error :as me]
    [clojurecamp.currmap.client.ui.common :as ui]
    [clojurecamp.currmap.client.state :as state]
    [clojurecamp.currmap.domain.schema :as schema]))

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
                            [{id-key id} label]))
                     ((fn [x]
                        (case (:db/cardinality schema)
                          :db.cardinality/one
                          (conj x [nil ""])
                          :db.cardinality/many
                          (identity x)))))
        ;; going through this hashing hoop to effectively allow for object values
        ;; b/c :value on option gets cast to string
        str-hash (fn [x] (str (hash x)))
        hash->value (zipmap (map str-hash (map first options))
                            (map first options))]
    [:div
     [:select {:tw "border border-gray-500 p-1"
               :default-value (case (:db/cardinality schema)
                                :db.cardinality/one
                                (str-hash value)
                                :db.cardinality/many
                                (map str-hash value))
               :multiple (case (:db/cardinality schema)
                           :db.cardinality/one
                           false
                           :db.cardinality/many
                           true)
               :on-change (fn [e]
                            (case (:db/cardinality schema)
                              :db.cardinality/one
                              (on-change (hash->value (.. e -target -value)))
                              :db.cardinality/many
                              (->> (.. e -target -selectedOptions)
                                   (mapv (fn [o] (hash->value (.-value o))))
                                   (on-change))))}
      (when (= :db/cardinality.one (:db/cardinality schema))
        [:option {:value "nil"} ""])
      (for [[value label] options]
        ^{:key (str-hash value)}
        [:option {:value (str-hash value)} label])]
     (when (= :db.cardinality/many (:db/cardinality schema))
       [:div {:tw "text-xs text-gray-500"}
       "(Hold ⌘ when clicking)"])]))

(defmethod input-view :default
  [{:keys [schema value]}]
  [:div {:tw "p-1"}
   (pr-str value)])

(defn editor-view
  [starter-entity]
  (r/with-let [entity (r/atom starter-entity)]
    (let [errors (me/humanize (m/explain (schema/malli-spec-for
                                           (schema/entity->entity-type @entity))
                                         @entity))]
      [:div.wrapper {:tw "absolute p-10 inset-1/4"}
       [:form.editor
        {:tw "bg-white border flex flex-col w-full h-full"
         :on-submit (fn [e]
                      (.preventDefault e)
                      (state/save-entity! @entity)
                      (state/close-editor!))}
        #_[:div {} (pr-str @entity)]
        #_[:div {} (pr-str errors)]
        [:table
         [:tbody
          (for [[k v] @entity]
            ^{:key k}
            [:tr
             [:td {:tw "p-1 align-top"} (pr-str k)]
             [:td
              [input-view
               {:schema (schema/attr->schema k)
                :value v
                :on-change (fn [new-value]
                             (swap! entity assoc k new-value))}]
              (when-let [error (get errors k)]
                [:div.error {:tw "text-red-500 flex items-center gap-1"}
                 [fa/fa-exclamation-triangle-solid {:tw "w-4 h-4"}]
                 (first error)])]])]]
        [:div.gap {:tw "grow"}]
        [:div {:tw "flex justify-between"}
         [ui/text-button
          {:label "Cancel"
           :variant :secondary
           :type "button"
           :on-click (fn []
                       (state/close-editor!))}]
         [ui/text-button
          {:label "Save"
           :disabled (seq errors)}]]]])))
