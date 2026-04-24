(ns clojurecamp.currmap.client.ui.badge-map
  (:require
   [bloom.commons.pages :as pages]
   [reagent.core :as r]
   [clojurecamp.currmap.client.ui.bezier :as bezier]
   [clojurecamp.currmap.client.state :as state]
   [goog.object :as o]))

(def level->roman
  {1 "I" 2 "II" 3 "III"})

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
                                                :width (* 7 (count group-name))
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

(defn pure-layout-view
  [{:keys [layout badges-by-id badge-states]}]
  [:svg {:style {:width (.-width layout)
                 :height (.-height layout)}}
   (for [{:strs [id x y width height children _edges]}
         (js->clj (.-children layout))]
     ^{:key id}
     (let [group-height 30]
       [:g {:transform (str "translate(" x "," y ")")}
        [:rect {:width width
                :height group-height
                :y (+ (get-in children [0 "y"])
                      (/ (get-in children [0 "height"])
                         2)
                      (- (/ group-height
                            2)))
                :fill "none"
                :stroke "#ccc"
                :stroke-width 1
                :rx 4}]
        (for [{:strs [id badge-id group-label width height x y]} children
              :let [;; force to be inline
                    #_#_y (get (first children) "y")]]
          ^{:key id}
          (if group-label
            [:text {:x x
                    :y (+ y 2 (/ height 2))
                    :text-anchor "start"
                    :alignment-baseline "middle"
                    :fill "#666"
                    :font-size 12}
             group-label]
            (let [badge (badges-by-id badge-id)
                  {:keys [granted? self-granted? in-progress?
                          on-path-to-working-towards? working-towards? viewing?]} badge-states]
              [:g {:tw "cursor-pointer"
                   :on-click (fn [] (pages/navigate-to! [:badge {:badge-id badge-id}]))}
               [:rect {:width width
                       :height height
                       :x x
                       :y y
                       :fill (cond
                               (granted? badge-id)
                               "#2563eb"

                               (self-granted? badge-id)
                               "#16a34a"

                               (in-progress? badge-id)
                               "#facc15"

                               (on-path-to-working-towards? badge-id)
                               "#db2777"

                               (working-towards? badge-id)
                               "#eab308"

                               (viewing? badge-id)
                               "#9333ea"

                               :else
                               "lightgray")}]
               [:text {:x (+ x (/ width 2))
                       :y (+ y 2 (/ height 2))
                       :text-anchor "middle"
                       :alignment-baseline "middle"
                       :fill (if (or (granted? badge-id)
                                     (self-granted? badge-id)
                                     (in-progress? badge-id)
                                     (on-path-to-working-towards? badge-id)
                                     (working-towards? badge-id)
                                     (viewing? badge-id))
                               "white"
                               "black")}
                (level->roman (:badge/level badge))]])))]))

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
