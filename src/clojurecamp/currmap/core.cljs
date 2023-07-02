(ns ^:figwheel-hooks
  clojurecamp.currmap.core
  (:require
   [reagent.dom :as rdom]
   [clojurecamp.currmap.ui.app :as app]))

(defn render []
  (rdom/render
   [app/app-view]
   (js/document.getElementById "app")))

(defn ^:export init []
  (render))

(defn ^:after-load reload
  []
  (render))
