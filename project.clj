(defproject clojurecamp.currmap "0.1.0-SNAPSHOT"

  :dependencies [[io.bloomventures/omni "0.32.2"]]

  :plugins [[io.bloomventures/omni "0.32.2"]]

  :omni-config clojurecamp.currmap.omni-config/omni-config

  :main clojurecamp.currmap.core

  :repl-options {:init-ns clojurecamp.currmap.core
                 :timeout 200000}

  :profiles {:uberjar
             {:aot :all
              :prep-tasks [["omni" "compile"]
                           "compile"]}})
