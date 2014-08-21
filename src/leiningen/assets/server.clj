(ns leiningen.assets.server
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.file-info :refer [wrap-file-info]]
            [clj-stacktrace.repl :refer [pst-str]]
            [clojure.string :as string]
            [clojure.java.io :as io]
            [hiccup.core :as hiccup]
            [hiccup.page :refer [html5]]
            [leiningen.core.eval :refer [eval-in-project]]))

(defn trim-slashes [s]
  (-> s
      (string/replace #"^/" "")
      (string/replace #"/$" "")))

(defmacro call [f & args]
  `((find-var '~f) ~@args))

(defn render-main-html [pkg]
  (when-let [path (:main-html pkg)]
    (slurp (call dar.assets/resource pkg path))))

(defn render-package-page [{pkg :main :as build} optimize?]
  (hiccup/html
   (html5
    [:html
     [:head
      [:title (or (:title pkg) (:name pkg))]
      [:style (:css-out build)]
      (if-not optimize?
        [:script {:src "/goog/base.js"}])
      [:script (:js-out build)]
      (if-not optimize?
        [:script (:js-require-call build)])]
     [:body (list
              (render-main-html pkg)
              [:script (:js-main-call build)])]])))

(defn send-package [name opts optimize?]
  (let [build (call dar.assets.builders/development name
                (if optimize?
                  (call dar.assets.builders.cljs/production opts)
                  (call dar.assets.builders.cljs/development opts)))]
    {:body (render-package-page build optimize?)}))

(defn text [status body]
  {:status status
   :headers {"content-type" "text/plain; charset=UTF-8"}
   :body body})

(defn send-exeption [ex]
  (text 500 (str "500 Internal Server Error\n\n" (pst-str ex))))

(defn base-handler [project opts]
  (fn [req]
    (let [path (-> req :uri (java.net.URI.) (.getPath) (trim-slashes))
          optimize? (.contains
                      (or (:query-string req) "")
                      "optimize")]
      (if (= "" path)
        (text 200 "Welcome to the assets dev server!")
        (let [f (io/file (:build-dir opts) path)]
          (if (and (.exists f) (not (.isDirectory f)))
            {:body f}
            (eval-in-project (assoc project :eval-in :classloader)
                             `(try
                                (require
                                  '[dar.assets]
                                  '[dar.assets.builders]
                                  '[dar.assets.builders.cljs])
                                (if (call dar.assets/assets-edn-url ~path)
                                  (send-package ~path ~opts ~optimize?)
                                  (text 404 "404 Not Found"))
                                (catch java.lang.Throwable ex#
                                  (send-exeption ex#))))))))))

(defn wrap-stacktrace [handler]
  (fn [req]
    (try
      (handler req)
      (catch Throwable ex
        (send-exeption ex)))))

(defn run [project opts]
  (let [handler (-> (base-handler project opts)
                    (wrap-file-info)
                    (wrap-stacktrace))]
    (run-jetty handler {:port (:server-port opts)})))
