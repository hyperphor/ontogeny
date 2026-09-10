(ns hyperphor.ontogeny.doc-gen
  "Writes a generated Alzabo schema to disk and renders its HTML doc under
  resources/public/schema/<slug>/ -- which hyperphor.way.handler's static
  resource middleware then serves directly, the same iframe-a-generated-doc
  trick nlq-aact uses for its own schema tab. Requires graphviz on PATH (see
  Alzabo's README); that's a deploy prerequisite for this app, not just a
  dev-machine one."
  (:require [clojure.string :as str]
            [hyperphor.alzabo.config :as alz-config]
            [hyperphor.alzabo.core :as alzabo]
            [hyperphor.alzabo.output :as alz-output]))

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

(defn generate
  "schema (an Alzabo schema map, e.g. from hyperphor.ontogeny.schema-gen/sgen)
  and the domain string it was generated from -> the URL path of its
  rendered HTML doc (e.g. \"/schema/jazz-musicians-abc123/index.html\"),
  ready to iframe or redirect to."
  [schema domain]
  (let [slug (slug domain)
        schema-file (str "resources/generated/" slug ".edn")
        output-dir (str "resources/public/schema/" slug "/")]
    (locking generation-lock
      (alz-output/write-schema schema schema-file)
      (alz-config/set-config! {:source schema-file :output-path output-dir :edge-labels? true})
      (alzabo/do-command :documentation {:schema-file schema-file}))
    (str "/schema/" slug "/index.html")))
