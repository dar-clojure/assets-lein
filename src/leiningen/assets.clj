(ns leiningen.assets)

(defmacro call [var & args]
  `((find-var '~var) ~@args))

(defn- server
  "Start a server for building and serving assets
  during development"
  ([project]
   (require '[leiningen.assets.server])
   (call leiningen.assets.server/run project)))

(defn- page
  "Build an assets component into a signle page application"
  ([project main]
   (require '[leiningen.assets.page])
   (call leiningen.assets.page/build project main)))

(defn assets
  {:subtasks [#'server #'page]}
  ([project cmd & args]
   (case cmd
     "server" (server project)
     "page" (page project (first args)))))
