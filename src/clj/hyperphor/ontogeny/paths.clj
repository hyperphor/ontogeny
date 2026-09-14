(ns hyperphor.ontogeny.paths
  "Shared local-storage location for this app's runtime-written files
  (generated schema docs, LLM-failure dumps). Deliberately the JVM's own
  temp dir, not a resources/... path: that only exists as loose files under
  `lein run`. Packaged as an uberjar, resources/** is baked into the
  (immutable) jar, and way's static middleware (resource/wrap-resource)
  reads it via the classpath -- so anything written to a resources/... path
  at runtime is never visible back out, no matter how successfully the
  write itself succeeds (this is what broke on Heroku: the write worked,
  nothing could ever serve it back). The JVM temp dir always exists and is
  always writable, dev machine or Heroku dyno alike -- Heroku's filesystem
  is transient (wiped on restart/deploy/scale), which is fine here: nothing
  under this root is expected to survive past the current dyno's lifetime.

  Callers ask for a file by purpose (schema-file, generated-file,
  failure-file) -- the directory layout underneath `root` is this
  namespace's business alone, not something doc-gen/handler/schema-gen
  should each independently know and reconstruct."
  (:require [clojure.java.io :as io]))

(def root
  (io/file (System/getProperty "java.io.tmpdir") "ontogeny"))

(defn schema-file
  "rel-path (a slug, or slug/some/file.html) -> the local File it lives at."
  [rel-path]
  (io/file root "schema" rel-path))

(defn generated-file
  "filename (e.g. \"<slug>.edn\") -> the local File for a generation's
  intermediate schema source, alzabo's input for rendering the HTML doc."
  [filename]
  (io/file root "generated" filename))

(defn failure-file
  "filename -> the local File for a dumped failed-LLM-transaction record."
  [filename]
  (io/file root "failures" filename))
