(defproject dar/assets-lein "0.0.1-SNAPSHOT"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :eval-in-leiningen true
  :dependencies [[clj-stacktrace "0.2.7"]
                 [ring/ring-jetty-adapter "1.2.1"]
                 [ring/ring-core "1.2.2"]
                 [dar/assets "0.0.1-SNAPSHOT"]
                 [dar/container "0.2.0-SNAPSHOT"]])
