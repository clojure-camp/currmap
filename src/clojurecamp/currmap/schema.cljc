(ns clojurecamp.currmap.schema
  (:require
    [bloom.commons.uuid :as uuid]
    [clojure.string :as string]
    [malli.generator :as mg]))

;; (WIP) malli specs

(def NonBlankString
  [:re #"\S+"])

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

(def id
  {:db/type :db.type/uuid
   :db/unique :db.unique/identity
   :db/spec :uuid})

(defn rel
  [cardinality entity-type]
  {:db/type :db.type/ref
   :db/cardinality (case cardinality
                    :one :db.cardinality/one
                    :many :db.cardinality/many)
   :db/spec (case cardinality
             :one :uuid
             :many [:uuid])
   :db/rel-entity-type entity-type
   :db/input :input/rel
   :db/user-editable? true})

(def schema
  {:topic
   {:topic/id id
    :topic/parent (rel :one :topic)
    :topic/name {:db/spec NonBlankString
                 :db/input :input/text
                 :db/user-editable? true}}

   :goal
   {:goal/id id
    :goal/topic (rel :one :topic)
    :goal/description {:db/spec [:maybe NonBlankString]
                       :db/user-editable? true}
    :goal/level {:db/spec Level
                 :db/user-editable? true}}

   :outcome
   {:outcome/id id
    :outcome/topic (rel :one :topic)
    :outcome/name {:db/spec NonBlankString}
    :outcome/description {:db/spec [:maybe NonBlankString]}
    :outcome/level {:db/spec Level}}

   :resource
   {:resource/id id
    :resource/outcome (rel :many :outcome)
    :resource/name {:db/spec NonBlankString}
    :resource/url {:db/spec URL}
    :resource/description {:db/spec [:maybe NonBlankString]}
    :resource/type {:db/spec [:enum :resource.type/todo]}}

   :rating
   {:rating/id id
    :rating/user (rel :one :user)
    :rating/resource (rel :one :resource)
    :rating/outcome (rel :one :outcome)
    :rating/value {:db/spec RatingValue}}

   :user
   {:user/id id
    :user/name {:db/spec NonBlankString}}})

(def datascript-schema
  (->> schema
       vals
       (apply concat)
       (into {})
       ((fn [x]
          (update-vals x #(select-keys % [:db/type
                                          :db/cardinality
                                          :db/unique]))))))

(defn id-key-for
  [entity-type]
  (keyword (name entity-type) "id"))

(defn name-key-for
  [entity-type]
  (keyword (name entity-type) "name"))

(defn malli-spec-for
  [entity-type]
  (into [:map]
        (->> (schema entity-type)
             (map (fn [[k opts]]
                    [k (:db/spec opts)])))))

#_(malli-spec-for :goal)

(defn pattern-for
  ;; ex "goal" -> [:goal/id ...]
  [entity-type]
  (->> (schema entity-type)
       (map (fn [[k opts]]
              (cond
                (= (:db/type opts) :db.type/ref)
                {k [(id-key-for (:db/rel-entity-type opts))]}
                :else
                k)))))

#_(pattern-for :goal)

(defn blank
  [entity-type]
  (-> (zipmap (keys (schema entity-type))
              (repeat nil))
      (assoc (id-key-for entity-type) (uuid/random))))

#_(blank :goal)
