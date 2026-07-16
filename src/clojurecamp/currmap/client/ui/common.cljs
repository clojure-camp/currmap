(ns clojurecamp.currmap.client.ui.common
  (:require
   [clojure.string :as string]))

(defn avatar-view
  [{:keys [name tw]}]
  [:div {:tw ["rounded bg-gray-300 flex items-center justify-center font-bold text-gray-500 shrink-0"
              (or tw "w-20 h-20 text-2xl")]}
   (some-> name (subs 0 1) string/upper-case)])

(defn icon-button
  [opts]
  [:button (merge (dissoc opts :icon)
                  {:tw "text-gray-500 hover:text-black"})
   [(:icon opts) {:tw "w-3 h-3"}]])

(defn text-button
  [{variant :variant :as opts :or {variant :primary}}]
  [:button (merge (dissoc opts :label :icon :variant)
                  {:tw ["flex text-sm items-center px-2 py-1 gap-1 "
                        "disabled:cursor-not-allowed disabled:line-through disabled:bg-gray-800 disabled:hover:bg-gray-800"
                        (case variant
                          :primary
                          "text-white bg-blue-500 hover:bg-blue-800 active:bg-black"
                          :secondary
                          "bg-gray-200 hover:bg-gray-300 active:bg-gray-500")]})
   (when (:icon opts)
     [(:icon opts) {:tw "w-3 h-3"}])
   (:label opts)])
