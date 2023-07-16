(ns clojurecamp.currmap.schema
  (:require
    [clojure.string :as string]))

;; (WIP) malli specs

(def NonBlankString
  [:and
   :string
   (fn [s] (not (string/blank? s)))])

(def Level
  [:enum
   :level/fundamentals
   :level/core
   :level/advanced])

(def RatingValue
  [:enum
   :rating.value/strong-no
   :rating.value/no
   :rating.value/yes
   :rating.value/strong-yes])

(def URL
  [:re #"https://.*"])

(def schema
  {:topic/id {:db/type :db.type/uuid
              :db/unique :db.unique/identity}
   :topic/parent {:db/type :db.type/ref
                  :db/cardinality :db.cardinality/one
                  :db/spec :uuid
                  :db/domain :topic/id
                  :db/input :input/id
                  :db/user-editable? true}
   :topic/name {:db/spec NonBlankString
                :db/input :input/text
                :db/user-editable? true}

   :goal/id {:db/type :db.type/uuid
             :db/unique :db.unique/identity}
   :goal/topic {:db/type :db.type/ref
                :db/cardinality :db.cardinality/one}
   :goal/description {:db/spec [:maybe NonBlankString]
                      :db/user-editable? true}
   :goal/level {:db/spec Level
                :db/user-editable? true}

   :outcome/id {:db/type :db.type/uuid
                :db/unique :db.unique/identity}
   :outcome/topic {:db/type :db.type/ref
                   :db/cardinality :db.cardinality/one}
   :outcome/name {:db/spec NonBlankString}
   :outcome/description {:db/spec [:maybe NonBlankString]}
   :outcome/level {:db/spec Level}

   :resource/id {:db/type :db.type/uuid
                 :db/unique :db.unique/identity}
   :resource/outcome {:db/type :db.type/ref
                      :db/cardinality :db.cardinality/many}
   :resource/name {:db/spec NonBlankString}
   :resource/url {:db/spec URL}
   :resource/description {:db/spec [:maybe NonBlankString]}
   :resource/type {}

   :rating/id {:db/type :db.type/uuid
               :db/unique :db.unique/identity}
   :rating/user {:db/type :db.type/ref
                 :db/cardinality :db.cardinality/one}
   :rating/resource {:db/type :db.type/ref
                     :db/cardinality :db.cardinality/one}
   :rating/outcome {:db/type :db.type/ref
                    :db/cardinality :db.cardinality/one}
   :rating/value {:db/spec RatingValue}

   :user/id {:db/type :db.type/uuid
             :db/unique :db.unique/identity}
   :user/name {:db/spec NonBlankString}
   })

(def datascript-schema
  schema)

(def pattern-for
  (->> schema
       (group-by (fn [[k _v]]
                   (namespace k)))
       (map (fn [[entity-type-string schema-entries]]
              [entity-type-string
               (->> schema-entries
                   (mapv (fn [[k _v]]
                          k)))]))
       (into {})))
