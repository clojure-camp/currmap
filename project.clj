(defproject clojurecamp.currmap "0.1.0-SNAPSHOT"

  :dependencies [[io.bloomventures/omni "0.32.2"]
                 [datascript "1.4.2"]
                 [tada "0.2.2"]
                 [metosin/malli "0.11.0"]
                 [com.draines/postal "2.0.3"]
                 [denistakeda/posh "0.5.9"]
                 [com.hyperfiddle/rcf "20220926-202227"]]

  :plugins [[io.bloomventures/omni "0.32.2"]]

  :omni-config clojurecamp.currmap.omni-config/omni-config

  :main clojurecamp.currmap.core

  :repl-options {:init-ns clojurecamp.currmap.core
                 :timeout 200000}

  :profiles {:dev
             {:source-paths ["dev-src"]}
             :uberjar
             {:aot :all
              :prep-tasks [["omni" "compile"]
                           "compile"]}})
