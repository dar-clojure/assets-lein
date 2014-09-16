(ns leiningen.assets.page
  (:require [dar.assets :as assets]
            [dar.assets.util :as util]
            [leiningen.core.eval :refer [eval-in-project]]))

(defn build [{{build-dir :build-dir} :assets :as p} main]
  (when-not build-dir
    (throw (IllegalArgumentException. ":build-dir is not specified in project.clj")))
  (when-not main
    (throw (IllegalArgumentException. "main component is not specified")))
  (eval-in-project (-> p
                     (assoc :eval-in :classloader)
                     (update-in [:dependencies] cons '[dar/assets "0.0.1"]))
    `(do
       (util/rmdir ~build-dir)
       (assets/build ~main ~build-dir))))
