(ns hyperphor.ontogeny.paths
  "Shared local-storage location for this app's runtime-written files
  (generated schema docs, LLM-failure dumps). Deliberately not a
  resources/... path: that only exists as loose files under `lein run`.
  Packaged as an uberjar, resources/** is baked into the (immutable) jar,
  and way's static middleware (resource/wrap-resource) reads it via the
  classpath -- so anything written to a resources/... path at runtime is
  never visible back out, no matter how successfully the write itself
  succeeds (this is what broke on Heroku: the write worked, nothing could
  ever serve it back).

  `root` is config-driven (:data-dir, env ONTOGENY_DATA_DIR), defaulting to
  a project-relative directory -- a real writable runtime filesystem path
  wherever the process's cwd is, dev machine or Heroku dyno alike, and
  unlike the JVM temp dir this used to default to, it survives a local
  restart. Doesn't change anything for Heroku: that filesystem is
  ephemeral (wiped on restart/deploy/scale) regardless of which real
  directory is chosen.

  Callers ask for a file by purpose (schema-file, generated-file,
  meta-file, failure-file) -- the directory layout underneath `root` is
  this namespace's business alone, not something doc-gen/handler/schema-gen
  should each independently know and reconstruct."
  (:require [clojure.java.io :as io]
            [hyperphor.way.config :as config]))

(defn root
  []
  (io/file (config/config :data-dir)))

(defn schema-file
  "rel-path (a slug, or slug/some/file.html) -> the local File it lives at."
  [rel-path]
  (io/file (root) "schema" rel-path))

(defn generated-file
  "filename (e.g. \"<slug>.edn\") -> the local File for a generation's
  intermediate schema source, alzabo's input for rendering the HTML doc."
  [filename]
  (io/file (root) "generated" filename))

(defn meta-file
  "filename (e.g. \"<slug>.edn\") -> the local File for a generation's
  directory-listing metadata record ({:slug :domain :created-at})."
  [filename]
  (io/file (root) "meta" filename))

(defn failure-file
  "filename -> the local File for a dumped failed-LLM-transaction record."
  [filename]
  (io/file (root) "failures" filename))
