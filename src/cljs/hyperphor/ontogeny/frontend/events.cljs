(ns hyperphor.ontogeny.frontend.events
  "App-db shape: {:domain \"...\" :extra \"...\" :provider \"anthropic\"
  :status nil/:pending/:done/:error :doc-path \"...\" :error-message \"...\"}.
  One generation in flight at a time -- no queue, matches the doc-gen lock
  on the backend.

  Generation runs as a background job server-side (Heroku's router
  hard-kills any request past 30s, and a real generation routinely takes
  2-5 minutes -- see hyperphor.ontogeny.handler): :ontogeny/generate kicks
  the job off and gets a job-id back immediately, then :ontogeny/poll
  re-checks its status every few seconds until :done/:error."
  (:require [re-frame.core :as rf]
            [hyperphor.way.api :as api]))

(def ^:private poll-interval-ms 3000)

(rf/reg-event-db
 :ontogeny/init
 (fn [db _]
   (merge db {:domain "" :extra "" :provider "anthropic" :status nil})))

(rf/reg-event-db
 :ontogeny/set-field
 (fn [db [_ field value]]
   (assoc db field value)))

;; Same pattern as hyperphor.nlq.frontend.qbox's :qbox-query -- fire the ajax
;; call as a side effect inside a plain reg-event-db handler (no :http-xhrio
;; effect is wired up anywhere in this stack), dispatching the response back
;; in on :handler/:error-handler.
(rf/reg-event-db
 :ontogeny/generate
 (fn [db _]
   (let [{:keys [domain extra provider]} db]
     (api/api-get "/ontogeny/generate"
                  {:params {:domain domain :extra extra :provider provider}
                   :handler #(rf/dispatch [:ontogeny/generate-started %])
                   :error-handler #(rf/dispatch [:ontogeny/generate-response {:error "Request failed -- see server logs."}])})
     (assoc db :status :pending :error-message nil))))

;; /generate only starts the job; a blank-domain (or other pre-job) failure
;; comes back as :error directly, with no job-id -- same shape the old
;; synchronous response used, so it flows straight to generate-response.
(rf/reg-event-fx
 :ontogeny/generate-started
 (fn [_ [_ {:keys [job-id error]}]]
   {:dispatch (if error
                [:ontogeny/generate-response {:error error}]
                [:ontogeny/poll job-id])}))

(rf/reg-event-fx
 :ontogeny/poll
 (fn [_ [_ job-id]]
   (api/api-get "/ontogeny/status"
                {:params {:job-id job-id}
                 :handler #(rf/dispatch [:ontogeny/poll-response job-id %])
                 :error-handler #(rf/dispatch [:ontogeny/generate-response {:error "Status check failed -- see server logs."}])})
   {}))

(rf/reg-event-fx
 :ontogeny/poll-response
 (fn [_ [_ job-id {:keys [status path message]}]]
   (case status
     :pending {:dispatch-later [{:ms poll-interval-ms :dispatch [:ontogeny/poll job-id]}]}
     :done    {:dispatch [:ontogeny/generate-response {:path path}]}
     :error   {:dispatch [:ontogeny/generate-response {:error message}]}
     {:dispatch [:ontogeny/generate-response {:error (str "Unexpected status: " status)}]})))

(rf/reg-event-db
 :ontogeny/generate-response
 (fn [db [_ {:keys [path error]}]]
   (if error
     (assoc db :status :error :error-message error)
     (assoc db :status :done :doc-path path))))

(rf/reg-sub
 :ontogeny/field
 (fn [db [_ field]] (get db field)))

(rf/reg-sub
 :ontogeny/status
 (fn [db _] (:status db)))

(rf/reg-sub
 :ontogeny/doc-path
 (fn [db _] (:doc-path db)))

(rf/reg-sub
 :ontogeny/error-message
 (fn [db _] (:error-message db)))
