(ns clojurecamp.currmap.omni-config
  (:require
    [clojurecamp.currmap.config :refer [config]]
    [clojurecamp.currmap.routes :as routes]))

(def omni-config
  {:omni/http-port (@config :http-port)
   :omni/title "Clojure Camp - Curriculum Map"
   :omni/environment (@config :environment)
   :omni/cljs {:main "clojurecamp.currmap.core"}
   :omni/auth (-> {:cookie {:name "clojurecamp-currmap"
                            :secret (@config :auth-cookie-secret)}
                   :token {:secret (@config :auth-token-secret)}})
   :omni/api-routes #'routes/routes})

