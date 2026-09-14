(ns hyperphor.ontogeny.core
  (:gen-class)
  (:require [clojure.java.shell :as shell]
            [hyperphor.way.server :as server]
            [hyperphor.way.config :as config]
            [hyperphor.ontogeny.handler :as handler]
            [hyperphor.multitool.cljcore :as ju]
            [taoensso.timbre :as log]
            [environ.core :as env]))

;; heroku-community/apt extracts graphviz's .deb via `dpkg -x` (files only,
;; no maintainer scripts), so its postinst -- which runs `dot -c` to build
;; the plugin-config cache enabling format plugins like SVG -- never runs.
;; Without it `dot -Tsvg` fails with "Format: svg not recognized" even
;; though the SVG plugin is installed; confirmed via `heroku run`. That
;; cache lives under the apt-installed graphviz tree (part of the runtime
;; filesystem, not the persisted slug -- see paths.clj's comment on the
;; classpath/filesystem distinction for the general shape of this class of
;; bug), so it doesn't survive from build to running dyno either way --
;; regenerate it here on every boot rather than depend on a buildpack hook.
;; No-op (a few ms) anywhere dot is already configured, e.g. brew's on a
;; dev machine.
(defn- ensure-graphviz-configured!
  []
  (let [{:keys [exit err]} (shell/sh "dot" "-c")]
    (when-not (zero? exit)
      (log/warn "dot -c failed (exit" exit "):" err))))

(defn -main
  [& args]
  (config/read-config "config.edn")
  (ensure-graphviz-configured!)
  (let [port (or (first args) (env/env :port))]
    (log/info "Starting ontogeny server on port" port)
    (server/start (Integer. port) (handler/app))
    ;; No-op on a real server (Heroku etc).
    (ju/open-url (format "http://localhost:%s" port))))
