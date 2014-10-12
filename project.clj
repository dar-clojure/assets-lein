(defproject dar/assets-lein "0.0.5"
  :description "Leiningen plugin for building assets"
  :url "https://github.com/dar-clojure/assets-lein"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :eval-in-leiningen true
  :dependencies [[clj-stacktrace "0.2.7"]
                 [ring/ring-jetty-adapter "1.2.1"]
                 [ring/ring-core "1.2.2"]
                 [dar/assets "0.0.3"]
                 [dar/container "0.2.0"]])
