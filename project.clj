(defproject
  coins "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url  "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [org.clojure/tools.logging "0.4.1"]
                 [cheshire "5.8.0"]
                 [clj-http "3.9.1"]]
  :plugins [[lein-cljfmt "0.6.1"]]
  :main coins.core
  :aot [coins.core]
  :target-path "target/%s"
  :profiles {:dev {:repl-options {:init-ns coins.core}}})