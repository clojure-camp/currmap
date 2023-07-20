(ns clojurecamp.currmap.domain.schema
  (:require
    [malli.core :as m]
    [bloom.commons.uuid :as uuid]))

(def Email
  ;; TODO could be better
  [:re {:error/message "should be an email"} #".*@.*\..*"])

(def NonBlankString
  [:re {:error/message "should not be blank"} #"\S+"])

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

(def OutcomeType
  [:enum
   :outcome.type/milestone])

(def URL
  [:re {:error/message "should be a link, starting with https://"} #"https://.*"])

(def id
  {:db/type :db.type/uuid
   :db/unique :db.unique/identity
   :db/spec :uuid})

(defn rel
  [cardinality entity-type required-or-optional]
  {:db/type :db.type/ref
   :db/cardinality (case cardinality
                     :one :db.cardinality/one
                     :many :db.cardinality/many)
   :db/spec (let [r [:map-of {:error/message "should be a related entity"}
                     [:and
                      :keyword
                      [:= (keyword
                            (name entity-type)
                            "id")]]
                     :uuid]
                  s (case cardinality
                      :one r
                      :many [:vector {:min 1} r])]
              (case required-or-optional
                :required
                s
                :optional
                [:maybe s]))
   :db/rel-entity-type entity-type
   :db/input :input/rel})

(def schema
  {:topic
   {:topic/id id
    :topic/parent (rel :one :topic :optional)
    :topic/name {:db/spec NonBlankString
                 :db/input :input/text}}

   :outcome
   {:outcome/id id
    :outcome/topic (rel :one :topic :required)
    :outcome/name {:db/spec NonBlankString
                   :db/input :input/text}
    :outcome/description {:db/spec [:maybe NonBlankString]
                          :db/input :input/text}
    :outcome/level {:db/spec Level
                    :db/input :input/radio}
    :outcome/type {:db/spec [:maybe OutcomeType]
                   :db/input :input/radio}}

   :resource
   {:resource/id id
    :resource/outcome (rel :many :outcome :optional)
    :resource/name {:db/spec NonBlankString
                    :db/input :input/text}
    :resource/url {:db/spec URL
                   :db/input :input/text}
    :resource/description {:db/spec [:maybe NonBlankString]
                           :db/input :input/text}}

   :rating
   {:rating/id id
    :rating/user (rel :one :user :required)
    :rating/resource (rel :one :resource :required)
    :rating/outcome (rel :one :outcome :required)
    :rating/value {:db/spec RatingValue
                   :db/input :input/radio}}

   :user
   {:user/id id
    :user/email {:db/spec Email}}})

(def datascript-schema
  (->> schema
       vals
       (apply concat)
       (into {})
       ((fn [x]
          (update-vals x #(select-keys % [:db/type
                                          :db/cardinality
                                          :db/unique]))))))

(defn attr->entity-type
  [attr]
  ;; choosing a random attributes namespace
  ;; TODO could look for id specifically
  (keyword (namespace attr)))

(defn entity->entity-type
  [entity]
  (attr->entity-type (key (first entity))))

#_(entity->entity-type {:topic/id "123"})

(defn attr->schema
  [attr]
  (get-in schema [(attr->entity-type attr) attr]))

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
             (map (fn [[attr opts]]
                    [attr (:db/spec opts)])))))

#_(malli-spec-for :topic)

(def Entity
  (into [:multi {:dispatch entity->entity-type}]
        (->> (keys schema)
             (map (fn [k]
                    [k (malli-spec-for k)])))))

;; given any entity, returns if valid
;; precompiled for performance
(def valid?
  (m/validator Entity))

#_(valid?
    {:user/id #uuid "577d2583-b74b-4bc8-9af2-0671964c83b4"
     :user/email "alice@example.com"})

(defn pattern-for
  ;; ex "topic" -> [:topic/id ...]
  [entity-type]
  (->> (schema entity-type)
       (map (fn [[attr opts]]
              (cond
                (= (:db/type opts) :db.type/ref)
                {attr [(id-key-for (:db/rel-entity-type opts))]}
                :else
                attr)))))

#_(pattern-for :topic)

(defn blank
  [entity-type]
  (-> (zipmap (keys (schema entity-type))
              (repeat nil))
      (assoc (id-key-for entity-type) (uuid/random))))

#_(blank :topic)
