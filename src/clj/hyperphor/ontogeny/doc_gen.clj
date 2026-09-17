(ns hyperphor.ontogeny.doc-gen
  "Writes a generated Alzabo schema to disk and renders its HTML doc under
  hyperphor.ontogeny.paths' local storage root -- hyperphor.ontogeny
  .handler serves /schema/<slug>/* from there directly (a dynamic
  filesystem route, not way's classpath-based static middleware, which
  can't see files written at runtime -- see paths.clj and handler.clj).
  Also writes a small metadata sidecar per generation for the /directory
  listing (see hyperphor.ontogeny.directory). Requires graphviz on PATH
  (see Alzabo's README); that's a deploy prerequisite for this app, not
  just a dev-machine one."
  (:require [hyperphor.alzabo.config :as alz-config]
            [hyperphor.alzabo.core :as alzabo]
            [hyperphor.alzabo.output :as alz-output]
            [hyperphor.ontogeny.paths :as paths]
            [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.util.zip ZipEntry ZipOutputStream]))

;; hyperphor.alzabo.config's `the-config` is a single global atom -- do-command
;; :documentation reads it mid-render (including shelling out to graphviz),
;; so two concurrent generations would race on it. Serializing here is the
;; simplest fix for a low-traffic demo app; revisit if that stops being true.
(def ^:private generation-lock (Object.))

(defn- slugify
  [s]
  (-> s str/lower-case (str/replace #"[^a-z0-9]+" "-") (str/replace #"(^-+|-+$)" "")))

(defn- slug
  "domain -> a filesystem/URL-safe, collision-resistant directory name."
  [domain]
  (str (slugify domain) "-" (Long/toString (System/currentTimeMillis) 36)))

(def ^:private primary-kind-count 5)

(defn- kind-refs
  "kind -> the set of other kind names its fields directly reference
  (tuple-typed fields expand to each component). Enum/primitive-typed
  fields aren't relations, same distinction alzabo.html/kind-relations
  draws for its own graphviz edges."
  [{:keys [kinds]} kind]
  (->> (get-in kinds [kind :fields])
       vals
       (mapcat (fn [{:keys [type]}] (if (vector? type) type [type])))
       (filter kinds)
       set))

(defn- relation-counts
  "kind -> number of relation edges touching it, counting both ends (a
  kind referenced by many others is just as \"primary\" as one with many
  outgoing reference fields)."
  [{:keys [kinds] :as schema}]
  (reduce (fn [counts kind]
            (reduce (fn [counts ref]
                      (-> counts (update kind (fnil inc 0)) (update ref (fnil inc 0))))
                    counts
                    (kind-refs schema kind)))
          {}
          (keys kinds)))

(defn- primary-kinds
  "schema -> the names of its most-connected kinds (by relation count,
  descending), for a short at-a-glance summary on the /directory listing."
  [schema]
  (->> (relation-counts schema)
       (sort-by val >)
       (take primary-kind-count)
       (mapv key)))

(defn- write-meta!
  "Records a successful generation for the /directory listing -- see
  hyperphor.ontogeny.directory."
  [slug domain schema]
  (let [f (paths/meta-file (str slug ".edn"))]
    (io/make-parents f)
    (spit f (pr-str {:slug slug :domain domain :created-at (System/currentTimeMillis)
                      :primary-kinds (primary-kinds schema)}))))

(defn generate
  "schema (an Alzabo schema map, e.g. from hyperphor.ontogeny.schema-gen/sgen)
  and the domain string it was generated from -> the URL path of its
  rendered HTML doc (e.g. \"/schema/jazz-musicians-abc123/index.html\"),
  ready to iframe or redirect to."
  [schema domain]
  (let [slug (slug domain)
        schema-file (str (paths/generated-file (str slug ".edn")))
        output-dir (str (paths/schema-file slug) "/")]
    (locking generation-lock
      (alz-output/write-schema schema schema-file)
      (alz-config/set-config! {:source schema-file :output-path output-dir :edge-labels? true})
      (alzabo/do-command :documentation {:schema-file schema-file}))
    (write-meta! slug domain schema)
    (str "/schema/" slug "/index.html")))

(defn schema-zip
  "slug -> zip file bytes of its rendered doc directory (everything under
  paths/schema-file slug), or nil if that slug hasn't been generated (or
  the storage root has been wiped -- see paths.clj)."
  [slug]
  (let [dir (paths/schema-file slug)]
    (when (.isDirectory dir)
      (let [baos (java.io.ByteArrayOutputStream.)
            base (.toPath dir)]
        (with-open [zos (ZipOutputStream. baos)]
          (doseq [f (file-seq dir)
                  :when (.isFile f)]
            (.putNextEntry zos (ZipEntry. (str (.relativize base (.toPath f)))))
            (io/copy f zos)
            (.closeEntry zos)))
        (.toByteArray baos)))))
