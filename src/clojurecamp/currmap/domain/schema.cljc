(ns clojurecamp.currmap.domain.schema
  (:require
   [bloom.commons.uuid :as uuid]
   [dat.malli :as dm]
   [malli.core :as m]
   [malli.transform :as mt])
  #?(:clj
     (:import
      [org.apache.commons.validator.routines UrlValidator])))

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
   :rating.value/weak-no
   :rating.value/weak-yes
   :rating.value/strong-yes])

(def OutcomeType
  [:enum
   :outcome.type/milestone])

(defn valid-url?
  [s]
  #?(:clj
     (let [url-validator (UrlValidator.)]
       (.isValid url-validator s))
     :cljs true))

#_(valid-url? "https://example.com")
#_(valid-url? "some junk")

(def URL
  [:re {:error/message "should be a link, starting with https://"} #"https://.*"])

(def id
  {:dat/type :db.type/uuid
   :dat/unique :dat.unique/identity})

(defn rel
  [cardinality entity-type required-or-optional]
  (let [rel-id-key (keyword (name entity-type) "id")
        spec (let [r [:map
                      [rel-id-key :uuid]]
                   s (case cardinality
                       :one r
                       :many [:sequential {:min 1} r])]
               (case required-or-optional
                 :required s
                 :optional [:maybe s]))]
    {:dat/rel [(case cardinality :one :dat.rel/one :many :dat.rel/many)
               entity-type
               rel-id-key]
     :dat/spec spec
     :db/input :input/rel}))

(defn rel? [v]
  (some? (:dat/rel v)))

(defn rel-entity-type [opts]
  (second (:dat/rel opts)))

(defn rel-cardinality [opts]
  (first (:dat/rel opts)))

(def schema
  {:topic
   {:topic/id id
    :topic/parent (rel :one :topic :optional)
    :topic/name {:dat/type :db.type/string
                 :dat/spec NonBlankString
                 :db/input :input/text}}

   :outcome
   {:outcome/id id
    :outcome/topic (rel :one :topic :required)
    :outcome/name {:dat/type :db.type/string
                   :dat/spec NonBlankString
                   :db/input :input/text}
    :outcome/description {:dat/type :db.type/string
                          :dat/spec [:maybe NonBlankString]
                          :db/input :input/text}
    :outcome/level {:dat/type :db.type/keyword
                    :dat/spec Level
                    :db/input :input/radio}
    :outcome/type {:dat/type :db.type/keyword
                   :dat/spec [:maybe OutcomeType]
                   :db/input :input/radio}}

   :resource
   {:resource/id id
    :resource/outcome (rel :many :outcome :optional)
    :resource/name {:dat/type :db.type/string
                    :dat/spec NonBlankString
                    :db/input :input/text}
    :resource/url {:dat/type :db.type/string
                   :dat/spec URL
                   :db/input :input/text}
    :resource/description {:dat/type :db.type/string
                           :dat/spec [:maybe NonBlankString]
                           :db/input :input/text}}

   :rating
   {:rating/id id
    :rating/user (rel :one :user :required)
    :rating/resource (rel :one :resource :required)
    :rating/outcome (rel :one :outcome :required)
    :rating/value {:dat/type :db.type/keyword
                   :dat/spec RatingValue
                   :db/input :input/radio}}

   :user
   {:user/id id
    :user/name {:dat/type :db.type/string
                :dat/spec NonBlankString
                :db/input :input/text}
    :user/email {:dat/type :db.type/string
                 :dat/spec Email}
    :user/badge-working-towards (rel :many :badge :optional)
    :user/badge-in-progress (rel :many :badge :optional)}

   :badge-group
   {:badge-group/id id
    :badge-group/name {:dat/type :db.type/string
                       :dat/spec NonBlankString
                       :db/input :input/text}}

   :badge
   {:badge/id id
    :badge/group (rel :one :badge-group :required)
    :badge/level {:dat/type :db.type/long
                  :dat/spec :pos-int
                  :db/input :input/text}
    :badge/prerequisite (rel :many :badge :optional)
    :badge/topic (rel :one :topic :required)
    :badge/outcome (rel :many :outcome :optional)
    :badge/name {:dat/type :db.type/string
                 :dat/spec NonBlankString
                 :db/input :input/text}
    :badge/description {:dat/type :db.type/string
                        :dat/spec [:maybe NonBlankString]
                        :db/input :input/text}}

   :assertion
   {:assertion/id id
    :assertion/badge (rel :one :badge :required)
    :assertion/user (rel :one :user :required)
    :assertion/issued-by (rel :one :user :required)
    :assertion/issued-at {:dat/type :db.type/instant
                          :dat/spec :inst
                          :db/input :input/datetime}}})

(defn attr->entity-type
  [attr]
  ;; choosing a random attributes namespace
  ;; TODO could look for id specifically
  (keyword (namespace attr)))

(defn entity->entity-type
  [entity]
  (->> (dissoc entity :db/id)
       keys
       (some (fn [k]
               (when (= "id" (name k))
                 (keyword (namespace k)))))))

#_(entity->entity-type {:topic/id "123"})

(defn attr->schema
  [attr]
  (get-in schema [(attr->entity-type attr) attr]))

#_(attr->schema :rating/user)

(defn id-key-for
  [entity-type]
  (keyword (name entity-type) "id"))

(defn name-key-for
  [entity-type]
  (keyword (name entity-type) "name"))

(defn rel-keys-for
  [entity-type]
  (->> (get schema entity-type)
       (filter (fn [[_attr v]]
                 (rel? v)))
       (map key)))

#_(rel-keys-for :resource)

(defn malli-spec-for
  [entity-type]
  (into [:map]
        (->> (schema entity-type)
             (keep (fn [[attr opts]]
                     (when-let [spec (dm/->malli-spec opts)]
                       [attr spec]))))))

#_(malli-spec-for :topic)

(def Entity
  (into [:multi {:dispatch entity->entity-type}]
        (->> (keys schema)
             (map (fn [k]
                    [k (malli-spec-for k)])))))

#_(def valid?
    (partial (m/validator Entity)))

(defn valid?
  [e]
  (m/validate Entity e))

#_(valid?
   {:user/id #uuid "577d2583-b74b-4bc8-9af2-0671964c83b4"
    :user/email "alice@example.com"})

#_(def strip-extra-keys
  (partial
   (m/decoder Entity mt/strip-extra-keys-transformer)))

(defn strip-extra-keys
  [e]
  (m/decode Entity e mt/strip-extra-keys-transformer))

#_(strip-extra-keys
   {:resource/id #uuid "395a1060-78c7-4ccf-9344-258c937ef4ed"
    :resource/url "https://caveman.mccue.dev/tutorial/clojure/3_start_an_nrepl_server"
    :resource/outcome [{:outcome/id #uuid "e1eb660f-9323-4239-bbe6-057ac230279a"
                        :outcome/name "Should be removed"
                        :outcome/level :level/core}]})

(defn pattern-for
  ;; ex "topic" -> [:topic/id ...]
  [entity-type]
  (->> (schema entity-type)
       (map (fn [[attr opts]]
              (cond
                (rel? opts)
                {attr [(id-key-for (rel-entity-type opts))]}
                :else
                attr)))))

#_(pattern-for :topic)

(defn blank
  [entity-type]
  (-> (zipmap (keys (schema entity-type))
              (repeat nil))
      (assoc (id-key-for entity-type) (uuid/random))))

#_(blank :topic)
