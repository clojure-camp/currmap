(ns clojurecamp.currmap.client.ui.badge-map
  (:require
   [bloom.commons.pages :as pages]
   [reagent.core :as r]
   [clojurecamp.currmap.client.ui.bezier :as bezier]
   [clojurecamp.currmap.client.state :as state]
   [goog.object :as o]))

(defn wait-for [get-value]
  (js/Promise.
   (fn [resolve]
     (let [cb (fn cb []
                (let [v (get-value)]
                  (if v
                    (resolve v)
                    (js/setTimeout cb 100))))]
       (cb)))))

(def node-height 20)

(defn ->elk [badges]
  {:id "root"
   :layoutOptions {:elk.algorithm "layered"
                   :elk.edgeRouting "SPLINES"
                   :elk.layered.spacing.nodeNodeBetweenLayers 50
                   :elk.layered.spacing.nodeNodeMin 50
                   :elk.layered.spacing.edgeNodeBetweenLayers 50
                   :elk.spacing.componentComponent 50}
   :children (->> badges
                  (map (fn [badge]
                         {:id (str (:badge/id badge))
                          :badge-id (:badge/id badge)
                          :width (* 10 (count (:badge/name badge)))
                          :height node-height})))
   :edges (->> badges
               (mapcat (fn [badge]
                         (->> badge
                              :badge/prerequisite
                              (map (fn [prereq]
                                     {:id (str (:badge/id prereq)
                                               (:badge/id badge))
                                      :sources [(str (:badge/id prereq))]
                                      :targets [(str (:badge/id badge))]}))))))})

(defn layout! [*layout badges]
  (let [elk (js/ELK.)]
    (-> ^js/Object elk
        (.layout (clj->js (->elk badges)))
        (.then (fn [l]
                 (reset! *layout l)))
        (.catch js/console.error))))

(defn pure-layout-view
  [{:keys [layout badges-by-id earned-badge-ids]}]
  [:svg {:style {:width (.-width layout)
                 :height (.-height layout)}}
   (for [{:strs [id badge-id width height x y]}
         (js->clj (.-children layout))
         :let [badge (badges-by-id badge-id)
               earned? (contains? earned-badge-ids badge-id)]]
     ^{:key id}
     [:g {:tw "cursor-pointer"
          :on-click
          (fn []
            (pages/navigate-to! [:badge {:badge-id badge-id}]))}
      [:rect {:width width
              :height height
              :x x
              :y y
              :fill (if earned? "#2563eb" "lightgray")}]
      [:text {:x (+ x (/ width 2))
              :y (+ y (/ height 2))
              :text-anchor "middle"
              :alignment-baseline "middle"
              :fill (if earned? "white" "black")}
       (:badge/name badge)]])

   (for [{:strs [id sections]}
         (js->clj (.-edges layout))]
     ^{:key id}
     [:g
      (for [{:strs [id] :as section} sections]
        ^{:key id}
        [:path {:d (bezier/get-bezier-path-from-points section)
                :stroke "black"
                :stroke-width 1
                :fill "none"}]
        #_[:line {:x1 (get startPoint "x")
                  :y1 (get startPoint "y")
                  :x2 (get endPoint "x")
                  :y2 (get endPoint "y")
                  :style {:stroke "black"
                          :strokeWidth 2}}])])])

(defn layout-view [badges]
  (r/with-let
    [*layout (r/atom nil)
     _ (-> (wait-for (fn []
                       (o/get js/window "ELK")))
           (.then (fn []
                    (layout! *layout badges))))]
    [:div {:tw "relative w-80vw h-90vh"}
     (when @*layout
       [pure-layout-view
        {:layout @*layout
         :badges-by-id (zipmap (map :badge/id badges)
                               badges)
         :earned-badge-ids (set @(state/q '[:find [?badge-id ...]
                                            :in $ ?user-id
                                            :where
                                            [?u :user/id ?user-id]
                                            [?a :assertion/user ?u]
                                            [?a :assertion/badge ?b]
                                            [?b :badge/id ?badge-id]]
                                          (:user/id @state/user)))}])]))
