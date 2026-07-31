(ns clojurecamp.currmap.client.ui.badges
  (:require
   [clojure.string :as str]
   [bloom.commons.pages :as pages]
   [clojurecamp.currmap.client.state :as state]))

(defn color [x]
  (str "oklch(60% 50%" (hash x) ")"))

(defn midtone [x]
  (str "oklch(80% 50%" (hash x) ")"))

(defn darker [x]
  (str "oklch(30% 50%" (hash x) ")"))

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

(defn badge-shape-view
  [{:keys [x y width height colors hover?]} badge badge-states]
  (let [cx (+ x (/ width 2))
        cy (+ y (/ height 2))
        outer-r (* (min width height) 0.5)
        inner-r (* outer-r 0.80)
        {:keys [highlight base midtone]} colors
        ;;    f                  shape     fill      stroke    text    h:fill  h:stroke  h:text   ribbon
        conf [[:granted?          ::star    highlight highlight base    base    highlight highlight true]
              [:self-granted?     ::star    highlight highlight base    base    highlight highlight false]
              [:in-progress?      ::star    midtone   midtone   base    midtone highlight highlight false]
              [:on-path-to-working-towards?
               ::hexagon midtone   midtone   base    midtone highlight highlight false]
              [:working-towards?  ::hexagon midtone   midtone   base    midtone highlight highlight false]
              [(constantly true) ::hexagon base      midtone   midtone base    highlight highlight false]]
        [shape fill stroke text h-fill h-stroke h-text ribbon?]
        (some (fn [[f & row]] (when (f badge-states) row)) conf)]
    [:g
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
      (level->roman (:badge/level badge))]]))

(defn badge-icon-view
  [{:keys [badge badge-states size]
    :or {size 20
         badge-states {}}}]
  (let [group-id (:badge-group/id (:badge/group badge))
        colors {:base (color group-id)
                :midtone (midtone group-id)
                :highlight "#fff"}]
    [:svg {:width size
           :height size
           :style {:overflow "visible"}}
     [badge-shape-view
      {:x 0
       :y 0
       :width size
       :height size
       :colors colors
       :hover? false}
      badge
      badge-states]]))

(defn badge-pill-view
  [badge-id]
  (let [badge @(state/pull-ident
                '[:badge/id
                  :badge/name
                  :badge/level
                  {:badge/group [:badge-group/name
                                 :badge-group/id]}]
                [:badge/id badge-id])
        badge-group (:badge/group badge)]
    [:a {:href (pages/path-for [:badge {:badge-id badge-id}])
         :tw "text-white text-sm rounded pl-1 pr-2 py-0.75 inline-flex items-center gap-1"
         :style {:background (color (:badge-group/id badge-group))}}
     [badge-icon-view {:badge badge
                       :badge-states (state/badge-states
                                      @state/user-badges-states
                                      badge-id)}]
     (:badge-group/name badge-group)
     " "
     (level->roman (:badge/level badge))]))
