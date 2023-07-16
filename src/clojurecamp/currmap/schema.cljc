(ns clojurecamp.currmap.schema)

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
