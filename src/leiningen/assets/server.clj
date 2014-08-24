(ns leiningen.assets.server
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.file-info :refer [wrap-file-info]]
            [clj-stacktrace.repl :refer [pst-str]]
            [clojure.string :as string]
            [clojure.java.io :as io]
            [hiccup.core :as hiccup]
            [hiccup.page :refer [html5]]
            [leiningen.core.eval :refer [eval-in-project]]))

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
        [:style (-> build :build :css :css)]
        (if-not optimize?
          [:script {:src "/goog/base.js"}])
        [:script (-> build :build :cljs :js)]
        (if-not optimize?
          [:script (-> build :build :cljs :require-call)])]
       [:body (list
                (render-main-html pkg)
                [:script (-> build :build :cljs :main-call)])]])))

(defn option [name q]
  (.contains q name))

(def last-cljs-options (atom nil))

(defn send-package [name opts query]
  (let [optimize? (option "optimize" query)
        fresh? (or
                 (option "fresh" query)
                 (not= @last-cljs-options (:cljs opts)))]
    (reset! last-cljs-options (:cljs opts))
    (when fresh?
      (call dar.assets/delete-build-dir opts)
      (call dar.assets.builders.cljs/clear-env))
    {:body (render-package-page
             (call dar.assets/build
               name
               (find-var 'dar.assets.builders.development/build)
               (if optimize?
                 (call dar.assets.builders.cljs/set-production-options opts)
                 (call dar.assets.builders.cljs/set-development-options opts)))
             optimize?)}))

(defn text [status body]
  {:status status
   :headers {"content-type" "text/plain; charset=UTF-8"}
   :body body})

(defn send-exeption [ex]
  (text 500 (str "500 Internal Server Error\n\n" (pst-str ex))))

(defn trim-slashes [s]
  (-> s
    (string/replace #"^/" "")
    (string/replace #"/$" "")))

(defn base-handler [project opts]
  (fn [req]
    (let [path (-> req :uri (java.net.URI.) (.getPath) (trim-slashes))]
      (if (= "" path)
        (text 200 "Welcome to the assets dev server!")
        (let [f (io/file (:build-dir opts) path)]
          (if (and (.exists f) (not (.isDirectory f)))
            {:body f}
            (eval-in-project (assoc project :eval-in :classloader)
              `(try
                 (require
                   '[dar.assets]
                   '[dar.assets.builders.development]
                   '[dar.assets.builders.cljs])
                 (if (call dar.assets/assets-edn-url ~path)
                   (send-package ~path ~opts ~(or (:query-string req) ""))
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
