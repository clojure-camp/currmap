(ns clojurecamp.currmap.client.ui.bezier
  (:require
   [clojure.string :as string]))

(defn pt->str [{:keys [x y]}]
  (str x " " y))

(defn mid-point [pt1 pt2]
  {:x (/ (+ (:x pt2) (:x pt1)) 2)
   :y (/ (+ (:y pt2) (:y pt1)) 2)})

;; https://github.com/eclipse-elk/elk/issues/848
(defn get-bezier-path-from-points
  [{:strs [startPoint bendPoints endPoint]}]
  (let [[start & control-points] (->> (concat [startPoint] bendPoints [endPoint])
                                      (map (fn [{:strs [x y]}]
                                             {:x x :y y})))
        path [(str "M " (pt->str start))]
        path (cond
               ;; if only one point, draw a straight line
               (= (count control-points) 1)
               (conj path (str "L " (pt->str (first control-points))))

               ;; if there are groups of 3 points, draw cubic bezier curves
               (zero? (mod (count control-points) 3))
               (into path
                     (for [i (range 0 (count control-points) 3)
                           :let [[c1 c2 p] (subvec (vec control-points) i (+ i 3))]]
                       (str "C " (pt->str c1) ", " (pt->str c2) ", " (pt->str p))))

               ;; if there's an even number of points, draw quadratic curves
               (even? (count control-points))
               (into path
                     (for [i (range 0 (count control-points) 2)
                           :let [[c p] (subvec (vec control-points) i (+ i 2))]]
                       (str "Q " (pt->str c) ", " (pt->str p))))

               ;; else, add missing points and try again
               :else
               (let [control-vec (vec control-points)
                     new-points (loop [points control-vec
                                       i (- (count control-vec) 3)]
                                  (if (< i 2)
                                    points
                                    (let [missing (mid-point (nth points (dec i)) (nth points i))
                                          updated (into (subvec points 0 i)
                                                        (cons missing (subvec points i)))]
                                      (recur updated (- i 2)))))]
                 (get-bezier-path-from-points (cons start new-points))))]

    (string/join " " path)))
