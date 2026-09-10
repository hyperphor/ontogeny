(ns hyperphor.ontogeny.handler
  (:require [clojure.string :as str]
            [compojure.core :refer [defroutes context GET]]
            [taoensso.timbre :as log]
            [hyperphor.way.handler :as wh]
            [hyperphor.ontogeny.schema-gen :as schema-gen]
            [hyperphor.ontogeny.doc-gen :as doc-gen]))

;;; GET, not POST -- matches the qbox-endpoint convention okc/eli/nlq-aact all
;;; use for their own single LLM-backed call (a query, not a state mutation
;;; in the usual REST sense, even though this one does write generated files).
(defn generate-endpoint
  "domain -> {:path <url of the rendered schema doc>}, or {:error <message>}
  on failure (a bad LLM response, a graphviz failure, etc -- generation is
  inherently flaky, surface the message rather than a bare 500)."
  [domain extra provider model]
  (if (str/blank? domain)
    {:error "domain is required"}
    (try
      (let [schema (schema-gen/sgen domain
                                     :extra (or extra "")
                                     :provider (keyword (if (str/blank? provider) "anthropic" provider))
                                     :model (when-not (str/blank? model) model))]
        {:path (doc-gen/generate schema domain)})
      (catch Exception e
        (log/warn e "Schema generation failed for domain" domain)
        {:error (str "Generation failed: " (ex-message e))}))))

(defroutes site-routes)

(defroutes api-routes
  (context "/api/ontogeny" []
    (GET "/generate" [domain extra provider model]
      (wh/content-response (generate-endpoint domain extra provider model)))))

;;; Warning: do not `(def app ...)` -- config isn't necessarily loaded yet at
;;; compile time (same caveat as okc/eli/nlq-aact's handler.clj).
(defn app
  []
  (wh/app site-routes api-routes))
