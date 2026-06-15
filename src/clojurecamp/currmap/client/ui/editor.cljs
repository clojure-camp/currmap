(ns clojurecamp.currmap.client.ui.editor
  (:require
    [clojure.string :as string]
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
   {:tw "w-full border border-gray-500 p-1"
    :default-value value
    :on-change (fn [e]
                 (let [v (.. e -target -value)]
                   (on-change (if (string/blank? v)
                                nil
                                v))))}])

(defmethod input-view :input/radio
  [{:keys [schema value on-change]}]
  [:div
   (for [option (->> (tree-seq vector? identity (:dat/spec schema))
                     (filter (fn [node]
                               (and
                                 (vector? node)
                                 (= :enum (first node)))))
                     first
                     rest
                     (cons nil))]
     ^{:key (or option "nil")}
     [:label {:tw "flex gap-1"}
      [:input {:type "radio"
               :checked (= value option)
               :on-change (fn [_]
                            (on-change option))}]
      (pr-str option)])])

(defn maybe-abbreviate [s length]
  (if (< length (count s))
    (str (apply str (take (dec length) s))
         "…")
    s))

(defmethod input-view :input/rel
  [{:keys [schema value on-change]}]
  ;; for a rel, value is expected to be, for example {:topic/id #uuid "..."}
  (let [id-key (schema/id-key-for (schema/rel-entity-type schema))
        name-key (schema/name-key-for (schema/rel-entity-type schema))
        options (->> @(state/q '[:find ?id ?name
                                 :in $ ?id-key ?name-key
                                 :where
                                 [?e ?id-key ?id]
                                 [?e ?name-key ?name]]
                               id-key name-key)
                     (map (fn [[id label]]
                            [{id-key id} (maybe-abbreviate label 40)]))
                     ((fn [x]
                        (case (schema/rel-cardinality schema)
                          :dat.rel/one
                          (conj x [nil ""])
                          :dat.rel/many
                          (identity x)))))
        ;; going through this hashing hoop to effectively allow for object values
        ;; b/c :value on option gets cast to string
        str-hash (fn [x] (str (hash (id-key x))))
        hash->value (zipmap (map str-hash (map first options))
                            (map first options))]
    [:div
     [:select {:tw ["border border-gray-500 p-1"
                    (case (schema/rel-cardinality schema)
                      :dat.rel/one
                      "1em"
                      :dat.rel/many
                      "h-20em")]
               :value (case (schema/rel-cardinality schema)
                        :dat.rel/one
                        (str-hash value)
                        :dat.rel/many
                        (map str-hash value))
               :multiple (case (schema/rel-cardinality schema)
                           :dat.rel/one
                           false
                           :dat.rel/many
                           true)
               :on-change (fn [e]
                            (case (schema/rel-cardinality schema)
                              :dat.rel/one
                              (on-change (hash->value (.. e -target -value)))
                              :dat.rel/many
                              (->> (.. e -target -selectedOptions)
                                   (mapv (fn [o] (hash->value (.-value o))))
                                   (on-change))))}
      (when (= :dat.rel/one (schema/rel-cardinality schema))
        [:option {:value "nil"} ""])
      (for [[value label] options]
        ^{:key (str-hash value)}
        [:option {:value (str-hash value)} label])]
     (when (= :dat.rel/many (schema/rel-cardinality schema))
       [:div {:tw "text-xs text-gray-500"}
       "(Hold ⌘ when clicking)"])]))

(defmethod input-view :default
  [{:keys [schema value]}]
  [:div {:tw "p-1"}
   (pr-str value)])

(defn embeddable-editor-view
  ;; entity is an atom
  [{:keys [entity]}]
  (let [errors (me/humanize (m/explain (schema/malli-spec-for
                                        (schema/entity->entity-type @entity))
                                       @entity))]
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
        :disabled (seq errors)}]]]) )

(defn editor-view
  [starter-entity]
  (r/with-let
   [entity (r/atom starter-entity)]
   [:div.wrapper {:tw "fixed p-10 inset-1/8 z-50"
                  :on-click (fn [e]
                              ;; b/c we have an on-click on root to close
                              ;; popover after every click
                              (.stopPropagation e))}
    [embeddable-editor-view {:entity entity}]]))
