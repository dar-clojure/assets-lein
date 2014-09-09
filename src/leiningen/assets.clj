(ns leiningen.assets)

(defn- server
  "Start a server for building and serving assets
  during development"
  ([project opts _]
   (require '[leiningen.assets.server])
   ((find-var 'leiningen.assets.server/run) project  opts)))

(defn assets
  {:help-arglists '([server])
   :subtasks [#'server]}
  ([project cmd & args]
   (let [opts (get project :assets {})]
     (case cmd
       "server" (server project opts args)))))
