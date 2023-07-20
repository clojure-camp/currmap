(ns seed
  (:require
    [bloom.commons.uuid :as uuid]
    [datascript.core :as d]
    [clojurecamp.currmap.db :as db]))

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

   {:outcome/id (id "outcome-atoms")
    :outcome/name "work with atoms"
    :outcome/description "TODO"
    :outcome/topic [:topic/id (id "topic-clojure-core")]
    :outcome/level :level/fundamentals}

   {:outcome/id (id "outcome-loop")
    :outcome/name "loop and recur"
    :outcome/description "TODO"
    :outcome/topic [:topic/id (id "topic-clojure-core")]
    :outcome/level :level/core}

   {:outcome/id (id "outcome-refs")
    :outcome/name "work with refs and agents"
    :outcome/description "TODO"
    :outcome/topic [:topic/id (id "topic-clojure-core")]
    :outcome/level :level/advanced}

   {:outcome/id (id "outcome-macro")
    :outcome/name "use a macro"
    :outcome/description "TODO"
    :outcome/topic [:topic/id (id "topic-clojure-macros")]
    :outcome/level :level/fundamentals}

   {:resource/id (id "resource-exercises")
    :resource/name "clojure.camp exercises"
    :resource/url "https://exercises.clojure.camp"
    :resource/description "TODO"
    :resource/outcome [[:outcome/id (id "outcome-loop")]
                       [:outcome/id (id "outcome-refs")]
                       [:outcome/id (id "outcome-macro")]]}

   {:resource/id (id "resource-other")
    :resource/name "other resource"
    :resource/url "https://example.com"
    :resource/description "TODO"
    :resource/outcome [[:outcome/id (id "outcome-loop")]
                       [:outcome/id (id "outcome-refs")]
                       [:outcome/id (id "outcome-macro")]]}

   {:resource/id (id "resource-three")
    :resource/name "third resource"
    :resource/url "https://example.com"
    :resource/description "TODO"
    :resource/outcome [[:outcome/id (id "outcome-loop")]
                       [:outcome/id (id "outcome-refs")]
                       [:outcome/id (id "outcome-macro")]]}

   {:resource/id (id "resource-four")
    :resource/name "fourth resource"
    :resource/url "https://example.com"
    :resource/description "TODO"
    :resource/outcome [[:outcome/id (id "outcome-loop")]
                       [:outcome/id (id "outcome-refs")]
                       [:outcome/id (id "outcome-macro")]]}

   {:user/id (id "user-alice")
    :user/email "alice@example.com"}

   {:user/id (id "user-bob")
    :user/email "bob@example.com"}

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

(defn seed! []
  (d/transact! @db/data seed-data))

#_(seed!)
