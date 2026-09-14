(ns hyperphor.ontogeny.handler
  (:require [clojure.string :as str]
            [compojure.core :refer [defroutes context GET]]
            [ring.middleware.file-info :refer [wrap-file-info]]
            [ring.util.response :as response]
            [taoensso.timbre :as log]
            [hyperphor.way.handler :as wh]
            [hyperphor.ontogeny.paths :as paths]
            [hyperphor.ontogeny.schema-gen :as schema-gen]
            [hyperphor.ontogeny.doc-gen :as doc-gen]))

;;; Generation runs as a background job, polled from the frontend -- Heroku's
;;; router hard-kills any request past 30s (H12, not configurable), and a
;;; real generation routinely takes 2-5 minutes. /generate only starts the
;;; job and returns a job-id immediately; /status is polled for the result.
;;; An atom of job-id -> {:status :pending/:done/:error ...} is enough for a
;;; low-traffic single-dyno demo app -- doc-gen's own lock already
;;; serializes the actual work, so concurrent jobs just queue behind it.
(defonce ^:private jobs (atom {}))

(def ^:private job-ttl-ms (* 60 60 1000)) ;prune finished jobs after an hour

(defn- prune-jobs
  [jobs-map]
  (let [cutoff (- (System/currentTimeMillis) job-ttl-ms)]
    (into {} (filter (fn [[_ {:keys [created-at]}]] (> created-at cutoff)) jobs-map))))

(defn- run-job!
  [job-id domain extra provider model]
  (future
    (try
      (let [schema (schema-gen/sgen domain :extra extra :provider provider :model model)
            path (doc-gen/generate schema domain)]
        (swap! jobs assoc-in [job-id :status] :done)
        (swap! jobs assoc-in [job-id :path] path))
      (catch Exception e
        (log/warn e "Schema generation failed for domain" domain)
        (swap! jobs assoc-in [job-id :status] :error)
        (swap! jobs assoc-in [job-id :message] (str "Generation failed: " (ex-message e)))))))

;;; GET, not POST -- matches the qbox-endpoint convention okc/eli/nlq-aact all
;;; use for their own single LLM-backed call (a query, not a state mutation
;;; in the usual REST sense, even though this one does write generated files).
(defn generate-endpoint
  "domain -> {:job-id <id to poll via status-endpoint>}, or {:error <message>}
  if domain is blank (no job created for that case)."
  [domain extra provider model]
  (if (str/blank? domain)
    {:error "domain is required"}
    (let [job-id (str (random-uuid))]
      (swap! jobs #(-> % prune-jobs (assoc job-id {:status :pending :created-at (System/currentTimeMillis)})))
      (run-job! job-id domain (or extra "")
                (keyword (if (str/blank? provider) "anthropic" provider))
                (when-not (str/blank? model) model))
      {:job-id job-id})))

(defn status-endpoint
  "job-id -> {:status :pending}, {:status :done :path ...}, or
  {:status :error :message ...}. Unknown/expired job-id also reports
  :error -- from the client's perspective a lost job looks the same as one
  that failed outright."
  [job-id]
  (-> (or (get @jobs job-id)
          {:status :error :message "Unknown or expired job"})
      (dissoc :created-at)))

;;; doc-gen renders under paths/root (a temp-dir path, see paths.clj); this
;;; serves it back. way's own static middleware (resource/wrap-resource) is
;;; classpath-based, so it can never see files written at runtime -- a
;;; jar's classpath is fixed at build time. This is a plain dynamic route
;;; reading straight off disk instead, guarded against path traversal.
;;; Nothing here is expected to survive a dyno restart -- see paths.clj.
(defn- schema-response
  [rel-path]
  (when (and (not (str/blank? rel-path))
             (not (str/includes? rel-path "..")))
    (response/file-response (str (paths/schema-file rel-path)))))

(defroutes site-routes
  (GET "/schema/:path{.*}" [path]
    (or (schema-response path)
        {:status 404 :body "Not found"})))

(defroutes api-routes
  (context "/api/ontogeny" []
    (GET "/generate" [domain extra provider model]
      (wh/content-response (generate-endpoint domain extra provider model)))
    (GET "/status" [job-id]
      (wh/content-response (status-endpoint job-id)))))

;;; Warning: do not `(def app ...)` -- config isn't necessarily loaded yet at
;;; compile time (same caveat as okc/eli/nlq-aact's handler.clj). wrap-file-info
;;; adds Content-Type/Last-Modified/ETag to schema-response's file-response.
(defn app
  []
  (-> (wh/app site-routes api-routes)
      wrap-file-info))
