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

(defmacro var* [v]
  `(find-var '~v))

(defn render-main-html [pkg]
  (when-let [f (:main-html pkg)]
    (-> (call dar.assets/resource pkg f)
        slurp)))

(defn render-package-page [pkg build]
  (hiccup/html
   (html5
    [:html
     [:head
      [:title (or (:title pkg) (:name pkg))]
      [:style (:css build)]
      [:script {:src "/goog/base.js"}]
      [:script (:js build)]]
     [:body (list
             (render-main-html pkg)
             (if-let [main (:main-ns pkg)]
               (list [:script (str "goog.require('" (namespace-munge main) "')")]
                     [:script (str (namespace-munge main) "._main()")])))]])))

(defn send-package [name opts]
  (let [pkg (call dar.assets/read name)
        opts (assoc opts :main-ns (:main-ns pkg))
        pkg-list (concat (:pre-include opts)
                         (:development pkg)
                         [name]
                         (:post-include opts))
        build (call dar.assets/build
                [(call dar.assets.builders.copy/copy :files)
                 (var* dar.assets.builders.css/build)
                 (var* dar.assets.builders.cljs/build)]
                opts
                pkg-list)]
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
                                (require
                                  '[dar.assets]
                                  '[dar.assets.utils]
                                  '[dar.assets.builders.copy]
                                  '[dar.assets.builders.css]
                                  '[dar.assets.builders.cljs])
                                (if (call dar.assets/assets-edn-url ~path)
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
