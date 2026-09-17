(ns hyperphor.ontogeny.frontend.core
  "App shell -- a domain-text form, then an iframe onto the generated schema
  doc once it's ready. One page, no tabs, modeled on eli/nlq-aact's own
  minimal frontend/core.cljs."
  (:require [re-frame.core :as rf]
            [hyperphor.way.ui.init :as init]
            [hyperphor.ontogeny.frontend.events]))

(defn- slug-from-doc-path
  "\"/schema/<slug>/index.html\" -> \"<slug>\", for the download link."
  [doc-path]
  (when doc-path
    (second (re-matches #"/schema/([^/]+)/.*" doc-path))))

(defn- domain-form
  [status doc-path]
  [:div.ontogeny-form
   [:label "Domain"
    [:input {:type "text"
             :placeholder "e.g. jazz musicians, cancer immunotherapy research"
             :value @(rf/subscribe [:ontogeny/field :domain])
             :on-change #(rf/dispatch [:ontogeny/set-field :domain (-> % .-target .-value)])}]]
   [:label "Extra instructions (optional)"
    [:textarea {:value @(rf/subscribe [:ontogeny/field :extra])
                :on-change #(rf/dispatch [:ontogeny/set-field :extra (-> % .-target .-value)])}]]
   [:label "Provider"
    [:select {:value @(rf/subscribe [:ontogeny/field :provider])
              :on-change #(rf/dispatch [:ontogeny/set-field :provider (-> % .-target .-value)])}
     [:option {:value "anthropic"} "Anthropic"]
     [:option {:value "openai"} "OpenAI"]]]
   [:button {:disabled (= status :pending)
             :on-click #(rf/dispatch [:ontogeny/generate])}
     (if (= status :pending) "Generating… (kinds, then fields, then rendering — usually 30-60s)" "Generate schema")]
   (if (= status :done)
     [:button {:on-click #(set! (.-location js/window)
                                 (str "/api/ontogeny/download?slug=" (slug-from-doc-path doc-path)))}
      "Download"]
     "Can take a couple of minutes, be patient")])

(defn app-ui
  []
  (let [status @(rf/subscribe [:ontogeny/status])
        doc-path @(rf/subscribe [:ontogeny/doc-path])
        error-message @(rf/subscribe [:ontogeny/error-message])]
    [:div.ontogeny-app
     [:div.site-hero
      [:h2 "Ontogeny"]
      [:p.tagline "Type a domain, get back a generated ontology."]]
     [domain-form status doc-path]
     (when (= status :error)
       [:div.ontogeny-error error-message])
     (when (= status :done)
       [:iframe.ontogeny-doc {:src doc-path}])]))

(defn ^:export init
  []
  (init/init app-ui #(rf/dispatch-sync [:ontogeny/init])))
