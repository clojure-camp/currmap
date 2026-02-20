(ns seed
  (:require
   [bloom.commons.uuid :as uuid]
   [clojure.edn :as edn]
   [clojurecamp.currmap.db :as db]
   [datascript.core :as d]))

(defn id [s]
  (uuid/from-email s))

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
    :user/email "alice@example.com"
    :user/name "Alice"}

   {:user/id (id "user-bob")
    :user/email "bob@example.com"
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
    :rating/value :rating.value/strong-yes}

   ;; badges and stuff

   {:badge/id (id "clojure-i-badge")
    :badge/topic [:topic/id (id "topic-clojure")]
    :badge/outcome [[:outcome/id (id "outcome-loop")]
                    [:outcome/id (id "outcome-atoms")]]
    :badge/name "Clojure I"}
   {:badge/id (id "clojure-ii-badge")
    :badge/prerequisite [[:badge/id (id "clojure-i-badge")]]
    :badge/topic [:topic/id (id "topic-clojure")]
    :badge/outcome [[:outcome/id (id "outcome-macro")]]
    :badge/name "Clojure II"}
   {:badge/id (id "clojure-iii-badge")
    :badge/prerequisite [[:badge/id (id "clojure-ii-badge")]]
    :badge/topic [:topic/id (id "topic-clojure")]
    :badge/outcome [[:outcome/id (id "outcome-refs")]]
    :badge/name "Clojure III"}

   {:user/id (id "user-alice")
    :user/badge-working-towards [[:badge/id (id "clojure-iii-badge")]]
    :user/badge-in-progress [[:badge/id (id "clojure-ii-badge")]]}

   {:assertion/id (id "assertion-1")
    :assertion/badge [:badge/id (id "clojure-i-badge")]
    :assertion/user [:user/id (id "user-alice")]
    :assertion/issued-by [:user/id (id "user-bob")]
    :assertion/issued-at #inst "2025-01-15T10:00:00.000-00:00"}

   {:assertion/id (id "assertion-2")
    :assertion/badge [:badge/id (id "clojure-ii-badge")]
    :assertion/user [:user/id (id "user-alice")]
    :assertion/issued-by [:user/id (id "user-alice")]
    :assertion/issued-at #inst "2024-01-15T10:00:00.000-00:00"}


   ])

(defn seed! []
  (d/transact! @db/data seed-data))

#_(seed!)

#_(d/transact @db/data [{:user/id (uuid/random)
                         :user/email "rafal.dittwald@gmail.com"}])

(defn badge-graph-entities []
  (->> (slurp "dev-resources/graph.edn")
       edn/read-string
       (mapcat (fn [[category chains]]
              (->> chains
                   (mapcat (fn [chain]
                          (->> chain
                               (cons nil)
                               (partition 2 1)
                               (map (fn [[prereq-id badge-id]]
                                      (merge
                                       {:badge/id (id badge-id)
                                        :badge/name badge-id}
                                       (when prereq-id
                                         {:badge/prerequisite [[:badge/id (id prereq-id)]]}))           ))))))))))

#_(d/transact @db/data (badge-graph-entities))
