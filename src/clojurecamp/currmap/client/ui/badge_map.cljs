(ns clojurecamp.currmap.client.ui.badge-map
  (:require
   [bloom.commons.pages :as pages]
   [clojure.string :as str]
   [reagent.core :as r]
   [clojurecamp.currmap.client.ui.bezier :as bezier]
   [clojurecamp.currmap.client.state :as state]
   [goog.object :as o]))

(defn color [x]
  (str "oklch(60% 50%" (hash x) ")"))

(defn midtone [x]
  (str "oklch(80% 50%" (hash x) ")"))

(def level->roman
  {1 "I" 2 "II" 3 "III"})

(defn star-points-str [cx cy outer-r inner-r]
  (->> (range 24)
       (map (fn [k]
              (let [angle (- (* k (/ js/Math.PI 12)) (/ js/Math.PI 2))
                    r (if (even? k) outer-r inner-r)]
                (str (+ cx (* r (js/Math.cos angle)))
                     ","
                     (+ cy (* r (js/Math.sin angle)))))))
       (str/join " ")))

(defn hexagon-points-str [cx cy r pointy-top?]
  (->> (range 6)
       (map (fn [k]
              (let [angle (- (* k (/ js/Math.PI 3)) (if pointy-top?
                                                      (/ js/Math.PI 2)
                                                      0))]
                (str (+ cx (* r (js/Math.cos angle)))
                     ","
                     (+ cy (* r (js/Math.sin angle)))))))
       (str/join " ")))

(defn ribbon-points-str [cx top-y]
  (let [length 15
        half-width 7]
    (str/join " "
              [(str (- cx half-width) "," top-y)
               (str (+ cx half-width) "," top-y)
               (str (+ cx half-width) "," (+ top-y length))
               (str cx "," (+ top-y (- length 3)))
               (str (- cx half-width) "," (+ top-y length))])))

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
  [{:keys [x y width height colors]} badge badge-states]
  (r/with-let
   [*hover? (r/atom false)]
   (let [badge-id (:badge/id badge)
         {:keys [granted? self-granted? in-progress?
                 on-path-to-working-towards? working-towards? viewing?]} badge-states
         hover? (or (viewing? badge-id)
                    @*hover?)
         cx (+ x (/ width 2))
         cy (+ y (/ height 2))
         outer-r (* (min width height) 0.5)
         inner-r (* outer-r 0.80)
         {:keys [highlight base midtone]} colors
         ;;    f                  shape     fill      stroke    text    h:fill  h:stroke  h:text   ribbon
         conf [[granted?          ::star    highlight highlight base    base    highlight highlight true]
               [self-granted?     ::star    highlight highlight base    base    highlight highlight false]
               [in-progress?      ::star    midtone   midtone   base    midtone highlight highlight false]
               [on-path-to-working-towards?
                                  ::hexagon midtone   midtone   base    midtone highlight highlight false]
               [working-towards?  ::hexagon midtone   midtone   base    midtone highlight highlight false]
               [(constantly true) ::hexagon base      midtone   midtone base    highlight highlight false]]
         [shape fill stroke text h-fill h-stroke h-text ribbon?]
         (some (fn [[f & row]] (when (f badge-id) row)) conf)]
     [:g {:tw "cursor-pointer"
          :on-click (fn [] (pages/navigate-to! [:badge {:badge-id badge-id}]))
          :on-mouse-enter (fn [] (reset! *hover? true))
          :on-mouse-leave (fn [] (reset! *hover? false))}
      (when ribbon?
        [:polygon {:points (ribbon-points-str cx (+ cy outer-r -3))
                   :fill midtone}])
      [:polygon {:points (case shape
                           ::star (star-points-str cx cy outer-r inner-r)
                           ::hexagon (hexagon-points-str cx cy outer-r false))
                 :fill (if hover? h-fill fill)
                 :stroke (if hover? h-stroke stroke)
                 :stroke-width 1}]
      [:text {:x cx
              :y (+ cy 1)
              :text-anchor "middle"
              :alignment-baseline "middle"
              :fill (if hover? h-text text)
              :font-size 10
              :pointer-events "none"}
       (level->roman (:badge/level badge))]])))

(defn pure-layout-view
  [{:keys [layout badges-by-id badge-states]}]
  [:svg {:style {:width (.-width layout)
                 :height (.-height layout)}}
   (for [{:strs [id x y width _height children _edges]}
         (js->clj (.-children layout))]
     ^{:key id}
     (let [group-height 30
           group-label (->> children
                            (map (fn [x]
                                   (get x "group-label")))
                            first)
           base-color (color group-label)
           midtone-color (midtone group-label)
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
                ;:stroke "#ccc"
                ;:stroke-width 1
                :rx 4}]
        (for [{:strs [id badge-id group-label width height x y]} children
              :let [;; force to be inline
                    #_#_y (get (first children) "y")]]
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
                         :height height}
             (badges-by-id badge-id)
             badge-states]))]))

   (for [{:strs [id sections]}
         (js->clj (.-edges layout))]
     ^{:key id}
     [:g
      (for [{:strs [id] :as section} sections]
        ^{:key id}
        [:path {:d (bezier/get-orthogonal-path-from-points section)
                :stroke "black"
                :stroke-width 1
                :fill "none"}]
        #_[:line {:x1 (get startPoint "x")
                  :y1 (get startPoint "y")
                  :x2 (get endPoint "x")
                  :y2 (get endPoint "y")
                  :style {:stroke "black"
                          :strokeWidth 2}}])])])

(defn layout-view
  [{:keys [badges active-badge-id]}]
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
         :badge-states {:granted? (set @(state/q '[:find [?badge-id ...]
                                                   :in $ ?user-id
                                                   :where
                                                   [?u :user/id ?user-id]
                                                   [?a :assertion/user ?u]
                                                   [?a :assertion/issued-by ?u2]
                                                   [(not= ?u ?u2)]
                                                   [?a :assertion/badge ?b]
                                                   [?b :badge/id ?badge-id]]
                                                 (:user/id @state/user)))
                        :self-granted? (set @(state/q '[:find [?badge-id ...]
                                                        :in $ ?user-id
                                                        :where
                                                        [?u :user/id ?user-id]
                                                        [?a :assertion/user ?u]
                                                        [?a :assertion/issued-by ?u]
                                                        [?a :assertion/badge ?b]
                                                        [?b :badge/id ?badge-id]]
                                                      (:user/id @state/user)))
                        :in-progress? (set @(state/q '[:find [?badge-id ...]
                                                       :in $ ?user-id
                                                       :where
                                                       [?u :user/id ?user-id]
                                                       [?u :user/badge-in-progress ?b]
                                                       [?b :badge/id ?badge-id]]
                                                     (:user/id @state/user)))
                        :on-path-to-working-towards? (set
                                                      ;; avoiding posh because it doesn't work well with rules
                                                      ;; therefore this query is not reactive
                                                      ;; but this component rerenders when working-towards? changes
                                                      ;; anyway, so it should be fine
                                                      (state/direct-q '[:find [?badge-id ...]
                                                                        :in $ ?user-id %
                                                                        :where
                                                                        [?u :user/id ?user-id]
                                                                        [?u :user/badge-working-towards ?b]
                                                                        (prerequisite ?b ?pb)
                                                                        [?pb :badge/id ?badge-id]]
                                                                      (:user/id @state/user)
                                                                      '[[(prerequisite ?badge ?p-badge)
                                                                         [?badge :badge/prerequisite ?p-badge]]
                                                                        [(prerequisite ?badge ?p-badge)
                                                                         [?badge :badge/prerequisite ?mid-badge]
                                                                         (prerequisite ?mid-badge ?p-badge)]]))
                        :working-towards? (set @(state/q '[:find [?badge-id ...]
                                                           :in $ ?user-id
                                                           :where
                                                           [?u :user/id ?user-id]
                                                           [?u :user/badge-working-towards ?b]
                                                           [?b :badge/id ?badge-id]]
                                                         (:user/id @state/user)))
                        :viewing? #{active-badge-id}}}])]))
