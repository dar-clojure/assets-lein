(ns leiningen.assets.server
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.file-info :refer [wrap-file-info]]
            [clj-stacktrace.repl :refer [pst-str]]
            [clojure.string :as string]
            [clojure.java.io :as io]
            [leiningen.core.eval :refer [eval-in-project]]
            [dar.assets :as assets]
            [dar.assets.util :as util]
            [dar.container :refer :all])
  (:import (java.util.concurrent Executors)))

(application optimized)

(include assets/production)

(define :assets/public-dir
  :args [:assets/build-dir]
  :fn identity)

(define :cljs/build-dir
  :args [:assets/public-dir]
  :fn identity)

(defn option? [name q]
  (.contains q name))

(defn text [status body]
  {:status status
   :headers {"content-type" "text/plain; charset=UTF-8"}
   :body body})

(defn send-exception [ex]
  (text 500 (str "500 Internal Server Error\n\n" (pst-str ex))))

(defn trim-slashes [s]
  (-> s
    (string/replace #"^/" "")
    (string/replace #"/$" "")))

(def options (atom nil))

(def project (atom nil))

(def app (atom nil))

(def optimize? (atom nil))

(defn reset-app [opt?]
  (reset! app (start
                (if opt? optimized assets/development)
                :env
                {:assets/build-dir (:build-dir @options)}))
  (reset! optimize? opt?)
  (util/rmdir (:build-dir @options)))

(defn get-app [q]
  (let [opt? (option? "optimize" q)
        fresh? (option? "fresh" q)]
    (when (or fresh? (not @app) (not= opt? @optimize?))
      (reset-app opt?))
    @app))

(defn send-package [q path]
  (<?!evaluate (start (get-app q) {:assets/main path})
    :page))

(defn handle-request [req]
  (let [path (-> req :uri (java.net.URI.) (.getPath) (trim-slashes))
        file (io/file (:build-dir @options) path)]
    (cond
      (= path "") (text 200 "Welcome to assets dev server!")
      (and (.exists file) (not (.isDirectory file))) {:body file}
      :else (eval-in-project @project
              `(try
                 (if (util/assets-edn-url ~path)
                   {:body (send-package ~(or (:query-string req) "") ~path)}
                   (text 404 "404 Not Found"))
                 (catch java.lang.Throwable ex#
                   (send-exception ex#)))))))

(defn wrap-stacktrace [handler]
  (fn [req]
    (try
      (handler req)
      (catch Throwable ex
        (send-exception ex)))))

(def queue (Executors/newSingleThreadExecutor))

(defn wrap-queue [handler]
  (fn [req]
    (.get (.submit queue #(handler req)))))

(defn check-options [{:keys [build-dir server-port]}]
  (when-not build-dir
    (throw (IllegalArgumentException. ":build-dir option is not specified in project.clj"))))

(defn run [{opts :assets :as p}]
  (check-options opts)
  (reset! project (-> p
                    (assoc :eval-in :classloader)
                    (update-in [:dependencies] cons '[dar/assets "0.0.1"])))
  (reset! options opts)
  (run-jetty (-> handle-request wrap-file-info wrap-stacktrace wrap-queue)
    {:port (or (:server-port opts) 3000)}))
