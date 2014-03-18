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

(defn render-package-page [pkg build]
  (hiccup/html
   (html5
    [:html
     [:head
      [:title (or (:title pkg) (:name pkg))]
      [:style (:css build)]
      [:script {:src "/goog/base.js"}]
      [:script (:js build)]]
     [:body
      (if-let [main (:main-ns pkg)]
        [:script (str "goog.require('" (namespace-munge main) "');")])]])))

(defn send-package [name opts]
  (let [pkg-list (conj (get opts :include []) name)
        build ((find-var 'dar.assets/build) pkg-list opts)
        pkg (-> build :packages last)]
    {:body (render-package-page pkg build)}))

(defn text [status body]
  {:status status
   :headers {"content-type" "text/plain; charset=UTF-8"}
   :body body})

(defn send-exeption [ex]
  (text 500 (str "500 Internal Server Error\n\n" (pst-str ex))))

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
                                (require '[dar.assets])
                                (if ((find-var 'dar.assets/assets-edn-url) ~path)
                                  (send-package ~path ~opts)
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
