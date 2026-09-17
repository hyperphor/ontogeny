(ns hyperphor.ontogeny.directory
  "The /directory page -- lists past generations from their metadata
  sidecars (hyperphor.ontogeny.doc-gen/write-meta!), newest first."
  (:require [hyperphor.ontogeny.paths :as paths]
            [hyperphor.way.views.html :as html]
            [clojure.edn :as edn]
            [clojure.java.io :as io]))

(defn- read-meta
  [f]
  (edn/read-string (slurp f)))

(defn generations
  "-> generation metadata records ({:slug :domain :created-at}), newest first."
  []
  (let [dir (io/file (paths/root) "meta")]
    (->> (when (.isDirectory dir) (.listFiles dir))
         (map read-meta)
         (sort-by :created-at >))))

(defn- row
  [{:keys [slug domain created-at]}]
  [:tr
   [:td [:a {:href (str "/schema/" slug "/index.html")} domain]]
   [:td (str (java.util.Date. (long created-at)))]
   [:td [:a {:href (str "/api/ontogeny/download?slug=" slug)} "Download"]]])

(defn page
  []
  (html/html-frame {} "Directory"
    [:div
     [:p [:a {:href "/"} "← Back"]]
     [:table.table
      [:thead [:tr [:th "Domain"] [:th "Generated"] [:th]]]
      [:tbody (map row (generations))]]]))
