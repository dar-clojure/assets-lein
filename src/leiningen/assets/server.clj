(ns leiningen.assets.server
  (:require [dar.assets :as assets]
            [ring.adapter.jetty :refer [run-jetty]]
            [clj-stacktrace.repl :refer [pst-str]]
            [clojure.string :as string]
            [clojure.java.io :as io]
            [hiccup.core :as hiccup]
            [hiccup.page :refer [html5]]))

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
      [:script (:js build)]]
     [:body
      (if-let [main (:main-ns pkg)]
        [:script (str "goog.require('" (namespace-munge main) "');")])]])))

(defn send-package [name opts]
  (let [pkg-list (conj (get opts :include []) name)
        build (assets/build pkg-list opts)
        pkg (-> build :packages last)]
    {:body (render-package-page pkg build)}))

(defn text [status body]
  {:status status
   :headers {"content-type" "text/plain; charset=UTF-8"}
   :body body})

(defn send-file [f]
  (if (.exists f)
    {:body f}
    (text 404 "404 Not found")))

(defn base-handler [opts]
  (fn [req]
    (let [path (-> req :uri (java.net.URI.) (.getPath) (trim-slashes))]
      (cond (= "" path) (text 200 "Welcome to the assets dev server!")
            (assets/assets-edn-url path) (send-package path opts)
            :else (send-file (io/file (:build-dir opts) path))))))

(defn wrap-stacktrace [handler]
  (fn [req]
    (try
      (handler req)
      (catch Throwable ex
        (text 500 (str "500 Internal Server Error\n\n" (pst-str ex)))))))

(defn run [opts]
  (let [handler (-> (base-handler opts) (wrap-stacktrace))]
    (run-jetty handler {:port (:server-port opts)})))
