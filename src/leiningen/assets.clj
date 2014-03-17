(ns leiningen.assets)

(defn- server
  "Start a server for building and serving assets
  during development"
  [opts _]
  (require '[leiningen.assets.server])
  ((find-var 'leiningen.assets.server/run)
   (merge {:build-dir "build/assets" :server-port 3000}
          opts)))

(defn assets
  {:help-arglists '([server])
   :subtasks [#'server]}
  ([project cmd & args]
   (let [opts (get project :assets {})]
     (case cmd
       "server" (server opts args)))))
