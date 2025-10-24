(defproject clojurecamp.currmap "0.1.0-SNAPSHOT"

  :dependencies [[io.bloomventures/omni "0.32.2"]
                 [io.bloomventures/commons "0.15.1"]
                 ;; require sci to fix edamame deps issue
                 [borkdude/sci "0.2.7"]
                 [datascript "1.4.2"]
                 [tada "0.2.2"]
                 [metosin/malli "0.19.1"]
                 [com.draines/postal "2.0.3"]
                 [denistakeda/posh "0.5.9"]
                 [commons-validator/commons-validator "1.10.0"]
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
