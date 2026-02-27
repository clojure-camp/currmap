(ns clojurecamp.currmap.client.tada
  (:require
   [reagent.core :as r]
   [bloom.commons.ajax :as ajax]))

(defn tada!
  [[event-id event-params]]
  (js/Promise.
   (fn [resolve reject]
     (ajax/request
      {:uri (str "/api/tada/"
                 (when (namespace event-id)
                   (str (namespace event-id) "."))
                 (name event-id))
       :method :POST
       :params {:event-id event-id
                :event-params event-params}
       :on-success resolve
       :on-error reject}))))

(defonce tada-atoms-cache (atom {}))

(defn tada-atom!
  [e]
  (if-let [a (get @tada-atoms-cache e)]
    a
    (let [refresh-fn (atom nil)
          a (let [a (with-meta (r/atom nil)
                      {::refresh-fn refresh-fn
                       ::error (r/atom nil)})]
              (swap! tada-atoms-cache assoc e a)
              a)
          f (fn []
              (-> (tada! e)
                  (.then (fn [v]
                           (reset! a v)))
                  (.catch (fn [err]
                            (swap! tada-atoms-cache update e vary-meta update ::error reset! err)))))]
      (reset! refresh-fn f)
      (f)
      a)))

(defn refresh!
  [a]
  (@(::refresh-fn (meta a))))

(defn error
  [a]
  @(::error (meta a)))

