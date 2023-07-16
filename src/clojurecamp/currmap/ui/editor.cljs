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

(defmethod input-view :default
  [{:keys [schema value]}]
  (pr-str value))

(defn editor-view
  [entity-ident]
  (r/with-let [pull-entity (state/pull'
                             (schema/pattern-for (namespace (first entity-ident)))
                             entity-ident)
               entity (r/atom @pull-entity)]
    [:div.wrapper {:tw "absolute p-10 inset-1/4"}
     [:div.editor {:tw "bg-white border w-full h-full"}
      [:form {:on-submit (fn [e]
                           (.preventDefault e)
                           (state/save-entity! @entity)
                           (state/close-editor!))}
       [:button {:tw "bg-blue-500 text-white p-1"} "Save"]
       [:table
        [:tbody
         (for [[k v] @entity]
           ^{:key k}
           [:tr
            [:td (pr-str k)]
            [:td [input-view
                  {:schema (schema/schema k)
                   :value v
                   :on-change (fn [new-value]
                                (swap! entity assoc k new-value))}]]])]]]]]))
