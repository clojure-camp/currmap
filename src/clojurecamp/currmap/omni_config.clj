(ns clojurecamp.currmap.omni-config
  (:require
    [clojurecamp.currmap.config :as config]
    [clojurecamp.currmap.routes :as routes]))

(def omni-config
  {:omni/http-port (config/get :http-port)
   :omni/title "Clojure Camp - Curriculum Map"
   :omni/environment (config/get :environment)
   :omni/cljs {:main "clojurecamp.currmap.client.core"}
   :omni/css {:tailwind? true}
   :omni/js-scripts [{:src "/popper.js"}
                     {:src "https://cdn.jsdelivr.net/npm/elkjs@0.11.1/lib/elk.bundled.js"}]
   :omni/auth {:cookie {:name "clojurecamp-currmap"
                        :secret (config/get :auth-cookie-secret)}
               :token {:secret (config/get :auth-token-secret)}}
   :omni/api-routes #'routes/routes})

