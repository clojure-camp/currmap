(ns clojurecamp.currmap.state
  (:require
    [bloom.commons.uuid :as uuid]
    [datascript.core :as d]
    [posh.reagent :as posh]))

(defonce id
  (memoize (fn [_seed]
             (uuid/random))))

(def seed-data
  [{:topic/id (id "topic-clojure")
    :topic/name "Clojure Programming"}
   {:topic/id (id "topic-clojure-core")
    :topic/name "Core"
    :topic/parent [:topic/id (id "topic-clojure")]}
   {:topic/id (id "topic-clojure-deeper")
    :topic/name "Depth test"
    :topic/parent [:topic/id (id "topic-clojure-core")]}
   {:topic/id (id "topic-clojure-macros")
    :topic/name "Macros"
    :topic/parent [:topic/id (id "topic-clojure")]}

   {:goal/id (id "goal-clojure-fundamentals")
    :goal/description  "do a Day 1 Advent of Code problem, without assistance, in under 45 minutes"
    :goal/topic [:topic/id (id "topic-clojure")]
    :goal/level :level/fundamentals}

   {:goal/id (id "goal-clojure-core")
    :goal/description "do a Day 5 Advent of Code problem, idiomatically, without assistance, in under 45 minutes"
    :goal/topic [:topic/id (id "topic-clojure")]
    :goal/level :level/core}

   {:outcome/id (id "outcome-atoms")
    :outcome/name "work with atoms"
    :outcome/topic [:topic/id (id "topic-clojure-core")]
    :outcome/level :level/fundamentals}

   {:outcome/id (id "outcome-loop")
    :outcome/name "loop and recur"
    :outcome/topic [:topic/id (id "topic-clojure-core")]
    :outcome/level :level/core}

   {:outcome/id (id "outcome-refs")
    :outcome/name "work with refs and agents"
    :outcome/topic [:topic/id (id "topic-clojure-core")]
    :outcome/level :level/advanced}

   {:outcome/id (id "outcome-macro")
    :outcome/name "use a macro"
    :outcome/topic [:topic/id (id "topic-clojure-macros")]
    :outcome/level :level/fundamentals}

   {:resource/id (id "resource-exercises")
    :resource/name "clojure.camp exercises"
    :resource/url "https://exercises.clojure.camp"
    :resource/description "TODO"
    :resource/outcome [[:outcome/id (id "outcome-loop")]
                       [:outcome/id (id "outcome-refs")]
                       [:outcome/id (id "outcome-macro")]]
    :resource/type :resource.type/todo}

   {:resource/id (id "resource-other")
    :resource/name "other resource"
    :resource/url "https://example.com"
    :resource/description "TODO"
    :resource/outcome [[:outcome/id (id "outcome-loop")]
                       [:outcome/id (id "outcome-refs")]
                       [:outcome/id (id "outcome-macro")]]
    :resource/type :resource.type/todo}

   {:resource/id (id "resource-three")
    :resource/name "third resource"
    :resource/url "https://example.com"
    :resource/description "TODO"
    :resource/outcome [[:outcome/id (id "outcome-loop")]
                       [:outcome/id (id "outcome-refs")]
                       [:outcome/id (id "outcome-macro")]]
    :resource/type :resource.type/todo}

   {:user/id (id "user-alice")
    :user/name "Alice"}

   {:user/id (id "user-bob")
    :user/name "Bob"}

   {:rating/id (id "rating-1")
    :rating/user [:user/id (id "user-alice")]
    :rating/resource [:resource/id (id "resource-exercises")]
    :rating/outcome [:outcome/id (id "outcome-macro")]
    :rating/value :rating.value/strong-yes}

   {:rating/id (id "rating-2")
    :rating/user [:user/id (id "user-bob")]
    :rating/resource [:resource/id (id "resource-exercises")]
    :rating/outcome [:outcome/id (id "outcome-macro")]
    :rating/value :rating.value/weak-yes}

   {:rating/id (id "rating-3")
    :rating/user [:user/id (id "user-bob")]
    :rating/resource [:resource/id (id "resource-other")]
    :rating/outcome [:outcome/id (id "outcome-macro")]
    :rating/value :rating.value/weak-no}

   {:rating/id (id "rating-4")
    :rating/user [:user/id (id "user-bob")]
    :rating/resource [:resource/id (id "resource-three")]
    :rating/outcome [:outcome/id (id "outcome-macro")]
    :rating/value :rating.value/strong-yes}])

(def schema
  {:topic/id {:db/type :db.type/uuid
              :db/unique :db.unique/identity}
   :topic/parent {:db/type :db.type/ref
                  :db/cardinality :db.cardinality/one}

   :goal/id {:db/type :db.type/uuid
             :db/unique :db.unique/identity}
   :goal/topic {:db/type :db.type/ref
                :db/cardinality :db.cardinality/one}

   :outcome/id {:db/type :db.type/uuid
                :db/unique :db.unique/identity}
   :outcome/topic {:db/type :db.type/ref
                   :db/cardinality :db.cardinality/one}

   :resource/id {:db/type :db.type/uuid
                 :db/unique :db.unique/identity}
   :resource/outcome {:db/type :db.type/ref
                      :db/cardinality :db.cardinality/many}

   :rating/id {:db/type :db.type/uuid
               :db/unique :db.unique/identity}
   :rating/user {:db/type :db.type/ref
                 :db/cardinality :db.cardinality/one}
   :rating/resource {:db/type :db.type/ref
                     :db/cardinality :db.cardinality/one}
   :rating/outcome {:db/type :db.type/ref
                    :db/cardinality :db.cardinality/one}

   :user/id {:db/type :db.type/uuid
             :db/unique :db.unique/identity}})

(defonce conn (d/create-conn schema))

(defonce _
  (do
    (posh/posh! conn)
    (d/transact! conn seed-data)
    nil))

(defn q
  [pattern & args]
  (apply posh/q pattern conn args))

(defn pull'
  [pattern [k v]]
  (let [eid (first (d/q '[:find [?e ...]
                          :in $ ?k ?v
                          :where
                          [?e ?k ?v]]
                        @conn
                        k v))]
    (posh/pull conn pattern eid)))

(defn pull
  [pattern eid]
  (apply posh/pull conn pattern eid))

(defn transact!
  [& args]
  (apply d/transact! conn args))


