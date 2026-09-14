(ns hyperphor.ontogeny.schema-gen
  "Domain description -> generated Alzabo schema. Ported from the
  hyperphor.alzabo.schema-gen-llm prototype (hyperphor/alzabo repo) -- the
  two-phase kinds-then-fields shape is the point of that design, kept as-is
  here. Differs from the prototype in two ways: calls hyperphor.ellum.core's
  current query/complete API (the prototype predates it and parsed raw
  OpenAI-shaped responses), and loads its example schema off the classpath
  instead of a repo-relative path that only resolved inside alzabo itself."
  (:require [clojure.string :as str]
            [clojure.java.io :as io]
            [taoensso.timbre :as log]
            [hyperphor.multitool.core :as u]
            [hyperphor.ellum.core :as llm]
            [hyperphor.ellum.extract :as llme]
            [hyperphor.ontogeny.paths :as paths]))

(def default-provider :anthropic)

(def system-prompt
  "You are a knowledge representation expert who knows how to create clean and elegant ontologies and schemas for various domains")

;; A classpath resource, not a filesystem path -- `lein run` and `java -jar`
;; have different working directories, and this needs to resolve under both.
(def ^:private sample-schema-resource "jazz-schema-example.edn")

(defn- sample-schema-text []
  (slurp (io/resource sample-schema-resource)))

;; Neither of hyperphor.ellum.extract's helpers is safe to use alone here:
;; extract-clojure only handles a ```-fenced response (returns nil otherwise,
;; no fallback), extract-edn falls back to bare read-string but then returns
;; an inconsistent shape -- the parsed value directly on that fallback path,
;; but a [value remaining-text] pair when the fence-matching path succeeds.
;; The model is inconsistent from call to call about whether it fences its
;; answer at all (confirmed empirically against both providers), so handle
;; both shapes ourselves rather than trust either helper's contract alone.
;;; → ellum
(defn extract-clojure
  [s]
  (try                                  ;TODO pull out into macro → way
    (let [[type code text] (llme/extract-code s)]
      (cond (or (= type :clojure) (= type :edn))
            [(read-string code) text]         ;TODO safety
            (nil? type)
            (when-let [e (read-string s)]
              [e ""])))
    (catch Exception e
      (throw (ex-info "Clojure extract failure" {:s s})))))

(defn extract-clojure-code
  [s]
  (-> s
      extract-clojure
      first))

;; On any failure calling or parsing an LLM response, dump everything known
;; about the transaction -- the request sent, and whatever the exception's
;; ex-data carried (extract-clojure's :s raw response text, or a hato HTTP
;; error's :status/:body/:headers) -- to a local file, and log a pointer to
;; it. Local storage here is fine even though Heroku's filesystem is
;; transient (see paths.clj): the point is to survive past the single
;; stack-trace line handler.clj otherwise surfaces, not past a dyno
;; restart -- `heroku logs` shows the dump path, `heroku run cat <path>`
;; (or a follow-up request while the dyno's still up) retrieves it.
(defn- dump-failure!
  [phase domain request e]
  (let [file (paths/failure-file (str (System/currentTimeMillis) "-" (name phase) ".edn"))]
    (io/make-parents file)
    (spit file (pr-str {:phase phase :domain domain :request request
                         :message (ex-message e) :data (ex-data e)}))
    (log/error e "LLM transaction failed for" phase domain "-- dumped to" (str file))))

;;; Phase 1: enumerate the kinds (entity types) for the domain before writing any fields.
;;; This forces the model to think about the full entity model first, so phase 2 can
;;; use reference types instead of lazily falling back to :string.
(defn- generate-kinds
  [domain extra provider model]
  (let [query (u/tx "List all significant entity types (kinds) needed for a {{domain}} domain schema. {{extra}}
Include not just the main entities but also supporting types that are often lazily represented as strings — things like anatomical parts, material types, classifications, controlled vocabularies, etc. that benefit from being first-class entities with their own attributes.
Return ONLY a Clojure map (no prose) of keyword kind-names to brief description strings.
Example: {:Fossil \"A preserved specimen\" :AnatomicalPart \"A body part or skeletal element\" :Taxon \"A taxonomic unit\"}")
        request (cond-> {:provider provider
                         :system system-prompt
                         :messages [{:role :user :content query}]}
                  model (assoc :model model))]
    (try
      (-> (llm/complete request) :content extract-clojure-code)
      (catch Exception e
        (dump-failure! :kinds domain request e)
        (throw e)))))

;;; Phase 2: generate full field definitions, with the kinds list in context so the model
;;; knows what reference types are available and uses them instead of :string.
(defn- generate-schema-from-kinds
  [domain kinds-map extra provider model]
  (let [kinds-list (str/join ", " (map name (keys kinds-map)))
        query (u/tx "Create a complete Alzabo schema for the {{domain}} domain using exactly these kinds: {{kinds-list}}.
For each kind, define its fields with :type, :cardinality (when :many), :doc, and for string fields :examples with 2-3 representative values.
IMPORTANT: whenever a field represents a concept that exists as a kind in the list above, use a reference type (the kind keyword) rather than :string.
{{extra}}")
        request (cond-> {:provider provider
                         :system system-prompt
                         :messages [{:role :user :content query}
                                    {:role :user :content (str "kinds with descriptions: " (pr-str kinds-map))}
                                    {:role :user :content (str "example schema format: " (sample-schema-text))}]
                         :max-tokens 120000} ;TODO this might be model-dependent, works for Anthropic default
                  model (assoc :model model))]
    (try
      (-> (llm/complete request) :content extract-clojure-code)
      (catch Exception e
        (dump-failure! :schema domain request e)
        (throw e)))))

(defn sgen
  "Generate an Alzabo schema (a Clojure map, not written to disk) for
  `domain`, a short domain-description string. `:extra` is optional
  additional instruction text appended to both LLM calls; `:provider`
  defaults to :anthropic; `:model` defaults to the provider's default.
  See hyperphor.alzabo.schema-gen-llm/sgen (alzabo repo), the prototype this
  is ported from -- known gap carried over as-is: it doesn't generate
  subtype/extends relations."
  [domain & {:keys [extra provider model] :or {extra "" provider default-provider}}]
  (let [kinds-map (generate-kinds domain extra provider model)]
    (prn :kinds kinds-map)
    (generate-schema-from-kinds domain kinds-map extra provider model)))
