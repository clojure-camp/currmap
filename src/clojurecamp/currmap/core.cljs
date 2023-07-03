(ns ^:figwheel-hooks
  clojurecamp.currmap.core
  (:require
   [bloom.omni.reagent :as rdom]
   [clojurecamp.currmap.ui.app :as app]))

(defn render []
  (rdom/render [app/app-view]))

(defn ^:export init []
  (render))

(defn ^:after-load reload
  []
  (render))
