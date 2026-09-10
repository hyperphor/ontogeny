(ns hyperphor.ontogeny.frontend.events
  "App-db shape: {:domain \"...\" :extra \"...\" :provider \"anthropic\"
  :status nil/:pending/:done/:error :doc-path \"...\" :error-message \"...\"}.
  One generation in flight at a time -- no queue, matches the doc-gen lock
  on the backend."
  (:require [re-frame.core :as rf]
            [hyperphor.way.api :as api]))

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
                   :handler #(rf/dispatch [:ontogeny/generate-response %])
                   :error-handler #(rf/dispatch [:ontogeny/generate-response {:error "Request failed -- see server logs."}])})
     (assoc db :status :pending :error-message nil))))

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
