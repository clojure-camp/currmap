(ns clojurecamp.currmap.domain.ratings
  (:refer-clojure :exclude [sort])
  (:require
    [hyperfiddle.rcf :as rcf]))

(def ->int
  {:rating.value/strong-no -6
   :rating.value/weak-no -2
   :rating.value/weak-yes 1
   :rating.value/strong-yes 5})

(defn remove-one [target coll]
  (let [[a b] (split-with (partial not= target) coll)]
    (concat a (rest b))))

(defn average [coll]
  (when (seq coll)
    (/ (reduce + coll) (count coll))))

(defn median
  "Note: returns a list of one or two values"
  [rating-values]
  (if (= 0 (count rating-values))
    nil
    (let [ratings* (sort-by ->int rating-values)]
      (if (even? (count rating-values))
        [(nth ratings* (dec (int (/ (count rating-values) 2))))
         (nth ratings* (int (/ (count rating-values) 2)))]
        [(nth ratings* (int (/ (count rating-values) 2)))]))))

(rcf/tests
  "median empty"
  (median [])
  := nil
  "median one"
  (median [:rating.value/weak-yes])
  := [:rating.value/weak-yes]
  "median two"
  (median [:rating.value/weak-no :rating.value/weak-yes])
  := [:rating.value/weak-no :rating.value/weak-yes]
  "median three"
  (median [:rating.value/weak-no :rating.value/weak-yes :rating.value/strong-no])
  := [:rating.value/weak-no]
  "median four"
  (median [:rating.value/weak-yes :rating.value/weak-no :rating.value/weak-yes :rating.value/strong-no])
  := [:rating.value/weak-no :rating.value/weak-yes]
  "median five"
  (median [:rating.value/weak-yes :rating.value/weak-no :rating.value/weak-no :rating.value/weak-yes :rating.value/strong-no])
  := [:rating.value/weak-no])

(defn compare-by-ratings
  [rating-values-a rating-values-b]
  (loop [rating-values-a rating-values-a
         rating-values-b rating-values-b]
    (let [median-a (median rating-values-a)
          median-b (median rating-values-b)
          result (compare (average (map ->int median-a))
                          (average (map ->int median-b)))]
      (if (and
            (= result 0)
            (seq rating-values-a)
            (seq rating-values-b))
        ;; remove one of the tied, and repeat
        (recur (remove-one (first median-a) rating-values-a)
               (remove-one (first median-b) rating-values-b))
        (* -1 result)))))

(defn sort
  "Given a coll, and fn that returns a list of rating values for each item in coll, returns the coll sorted by the rating values.
    Expects rating values to be one of: :rating.value/strong-no, :rating.value/weak-no, :rating.value/weak-yes, :rating.value/strong-yes"
  [kfn coll]
  (clojure.core/sort (fn [a b]
                       (compare-by-ratings (kfn a) (kfn b))) coll))

