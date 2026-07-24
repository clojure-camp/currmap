(ns clojurecamp.currmap.client.ui.badge-map
  (:require
   [bloom.commons.pages :as pages]
   [reagent.core :as r]
   [clojurecamp.currmap.client.ui.bezier :as bezier]
   [clojurecamp.currmap.client.state :as state]
   [clojurecamp.currmap.client.ui.badges :as badges]
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

(defn port-id->badge-id [port-id]
  (subs port-id 0 (- (count port-id) 2)))

(def group-label-left-pad 5)

(defn ->elk [badges]
  (let [groups (->> badges
                    (group-by (fn [b] (:badge-group/id (:badge/group b)))))
        badge->group (->> badges
                          (map (fn [b]
                                 [(:badge/id b)
                                  (:badge-group/id (:badge/group b))]))
                          (into {}))
        all-edges (->> badges
                       (mapcat (fn [badge]
                                 (->> badge
                                      :badge/prerequisite
                                      (map (fn [prereq]
                                             (let [intra? (= (badge->group (:badge/id prereq))
                                                             (badge->group (:badge/id badge)))]
                                               {:id (str (:badge/id prereq) (:badge/id badge))
                                                :sources [(str (:badge/id prereq) (if intra? "-E" "-S"))]
                                                :targets [(str (:badge/id badge) (if intra? "-W" "-N"))]
                                                :source-group (badge->group (:badge/id prereq))
                                                :target-group (badge->group (:badge/id badge))})))))))
        intra-group-edges (->> all-edges
                               (filter (fn [e]
                                         (= (:source-group e) (:target-group e))))
                               (group-by :source-group))
        inter-group-edges (->> all-edges
                               (remove (fn [e]
                                         (= (:source-group e) (:target-group e)))))
        clean-edge (fn [e]
                     (dissoc e :source-group :target-group))
        badge-node (fn [badge]
                     (let [badge-id (:badge/id badge)]
                       {:id (str badge-id)
                        :badge-id badge-id
                        :width 20
                        :height node-height
                        :layoutOptions {:elk.portConstraints "FIXED_SIDE"}
                        :ports [{:id (str badge-id "-N") :properties {:port.side "NORTH" #_#_:port.borderOffset 10}}
                                {:id (str badge-id "-S") :properties {:port.side "SOUTH" #_#_:port.borderOffset 10}}
                                {:id (str badge-id "-E") :properties {:port.side "EAST" #_#_:port.borderOffset 10}}
                                {:id (str badge-id "-W") :properties {:port.side "WEST" #_#_:port.borderOffset 10}}]}))]
    {:id "root"
     :layoutOptions {:elk.algorithm "layered"
                     ;:elk.edgeRouting "SPLINES"
                     :elk.hierarchyHandling "INCLUDE_CHILDREN"
                     :elk.layered.spacing.nodeNodeBetweenLayers 100
                     :elk.layered.spacing.nodeNodeMin 50
                     :elk.layered.spacing.edgeNodeBetweenLayers 50
                     :elk.spacing.componentComponent 50}
     :children (->> groups
                    (map (fn [[group-id group-badges]]
                           (let [group-name (:badge-group/name (:badge/group (first group-badges)))
                                 sorted-badges (sort-by :badge/level group-badges)
                                 first-badge-id (:badge/id (first sorted-badges))
                                 label-id (str group-id "-label")]
                             {:id (str group-id)
                              :layoutOptions {:elk.direction "RIGHT"
                                              :elk.padding "[top=3,right=5,bottom=3,left=5]"
                                              :elk.layered.spacing.nodeNodeBetweenLayers 5}
                              :children (into [{:id label-id
                                                :group-id group-id
                                                :group-label group-name
                                                :width (+ group-label-left-pad (* 7 (count group-name)))
                                                :height node-height
                                                :layoutOptions {:elk.portConstraints "FIXED_SIDE"}
                                                :ports [{:id (str label-id "-E")
                                                         :properties {:port.side "EAST"
                                                                      }}]}]
                                              (map badge-node sorted-badges))
                              :edges (into [{:id (str label-id "-edge")
                                             :sources [(str label-id "-E")]
                                             :targets [(str first-badge-id "-W")]}]
                                           (->> (get intra-group-edges group-id)
                                                (map clean-edge)))}))))
     :edges (map clean-edge inter-group-edges)}))

(defn layout! [*layout badges]
  (let [elk (js/ELK.)]
    (-> ^js/Object elk
        (.layout (clj->js (->elk badges)))
        (.then (fn [l]
                 (reset! *layout l)))
        (.catch js/console.error))))

(defn badge-view
  [{:keys [x y width height colors *hovered-badge-id]} badge badge-states]
  (r/with-let
   [*hover? (r/atom false)]
   (let [badge-id (:badge/id badge)
         hover? (or (:viewing? badge-states) @*hover?)]
     [:g {:tw "cursor-pointer"
          ;; keep mousedown from starting a pan on the container
          :on-mouse-down (fn [e] (.stopPropagation e))
          :on-click (fn [] (pages/navigate-to! [:badge {:badge-id badge-id}]))
          :on-mouse-enter (fn []
                            (reset! *hover? true)
                            (reset! *hovered-badge-id (str badge-id)))
          :on-mouse-leave (fn []
                            (reset! *hover? false)
                            (reset! *hovered-badge-id nil))}
      [badges/badge-shape-view
       {:x x
        :y y
        :width width
        :height height
        :colors colors
        :hover? hover?}
       badge
       badge-states]])))

(defn pure-layout-view
  [{:keys [layout badges-by-id badges-states]}]
  (r/with-let
   [*hovered-badge-id (r/atom nil)]
   [:svg {:tw "block shrink-0 m-auto"
          :style {:width (.-width layout)
                  :height (.-height layout)}}
   (for [{:strs [id x y width _height children _edges]}
         (js->clj (.-children layout))]
     ^{:key id}
     (let [group-height 30
           group-id (->> children
                            (map (fn [x]
                                   (get x "group-id")))
                            first)
           base-color (badges/color group-id)
           midtone-color (badges/midtone group-id)
           highlight-color "#fff"]
       [:g {:transform (str "translate(" x "," y ")")}
        [:rect {:width width
                :height group-height
                :y (+ (get-in children [0 "y"])
                      (/ (get-in children [0 "height"])
                         2)
                      (- (/ group-height
                            2)))
                :fill base-color
                :rx 4}]
        (for [{:strs [id badge-id group-label width height x y]} children]
          ^{:key id}
          (if group-label
            [:text {:x (+ x group-label-left-pad)
                    :y (+ y 2 (/ height 2))
                    :text-anchor "start"
                    :alignment-baseline "middle"
                    :fill "#fff"
                    :font-size 12}
             group-label]
            [badge-view {:x x
                         :y y
                         :colors {:base base-color
                                  :midtone midtone-color
                                  :highlight highlight-color}
                         :width width
                         :height height
                         :*hovered-badge-id *hovered-badge-id}
             (badges-by-id badge-id)
             (state/badge-states
              badges-states
              badge-id)]))]))

   (for [{:strs [id sections connected?]}
         (->> (js->clj (.-edges layout))
              (map (fn [{:strs [sources targets] :as edge}]
                     (assoc edge "connected?"
                            (and @*hovered-badge-id
                                 (or (some #(= (port-id->badge-id %)
                                               @*hovered-badge-id)
                                           sources)
                                     (some #(= (port-id->badge-id %)
                                               @*hovered-badge-id)
                                           targets))))))
              (sort-by (fn [edge]
                         (get edge "connected?"))))]
     ^{:key id}
     [:g
      (for [{:strs [id] :as section} sections]
        ^{:key id}
        [:path {:d (bezier/get-orthogonal-path-from-points section)
                :stroke (if connected? "#000" "#ccc")
                :stroke-width 1
                :fill "none"}])])]))

(defn layout-view
  [{:keys [badges active-badge-id]}]
  (r/with-let
    [*layout (r/atom nil)
     *container (r/atom nil)
     *drag (r/atom nil)
     _ (-> (wait-for (fn []
                       (o/get js/window "ELK")))
           (.then (fn []
                    (layout! *layout badges))))]
    [:div {:ref (fn [el] (reset! *container el))
           :tw ["relative w-full h-full overflow-auto select-none flex"
                (if @*drag "cursor-grabbing" "cursor-grab")]
           :on-mouse-down (fn [e]
                            (when-let [el @*container]
                              (.preventDefault e)
                              (reset! *drag {:start-x (.-clientX e)
                                             :start-y (.-clientY e)
                                             :scroll-left (.-scrollLeft el)
                                             :scroll-top (.-scrollTop el)})))
           :on-mouse-move (fn [e]
                            (when-let [d @*drag]
                              (when-let [el @*container]
                                (set! (.-scrollLeft el)
                                      (- (:scroll-left d) (- (.-clientX e) (:start-x d))))
                                (set! (.-scrollTop el)
                                      (- (:scroll-top d) (- (.-clientY e) (:start-y d)))))))
           :on-mouse-up (fn [] (reset! *drag nil))
           :on-mouse-leave (fn [] (reset! *drag nil))}
     (when @*layout
       [pure-layout-view
        {:layout @*layout
         :badges-by-id (zipmap (map :badge/id badges)
                               badges)
         :badges-states (assoc
                        @state/user-badges-states
                        :viewing? #{active-badge-id})}])]))
