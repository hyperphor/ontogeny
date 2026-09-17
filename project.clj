(defproject hyperphor/ontogeny "0.1.0"
  :description "Ontogeny: type a domain description, get back a generated Alzabo
                schema and its rendered HTML doc. See design/design.md."
  :url "https://github.com/hyperphor/ontogeny"
  :plugins [[lein-shadow "0.4.1"]]
  ;; reagent/re-frame/cljs-ajax/accountant/secretary and the whole ring+compojure
  ;; backend stack come transitively via com.hyperphor/way's own project.clj --
  ;; only what ontogeny's own code requires directly needs to be listed here.
  :dependencies [[org.clojure/clojure "1.12.5"]
                 [com.hyperphor/way "0.2.7"]
                 [com.hyperphor/ellum "0.1.3"]
                 ;; NOT excluding hiccup, unlike okc/nlflame's pin -- Alzabo's
                 ;; HTML doc generation (what this whole app is for) needs it.
                 [com.hyperphor/alzabo "1.3.8"]
                 [com.taoensso/timbre "6.7.1"]
                 [environ "1.2.0"]
                 ;; Direct dep, NOT :dev-profile-only -- `lein uberjar` activates
                 ;; :uberjar, not :dev, so a :dev-scoped shadow-cljs is invisible
                 ;; to :uberjar's own ["shadow" "release" "app"] prep-task below
                 ;; (same reason okc/eli/nlq-aact keep it direct).
                 [thheller/shadow-cljs "3.1.8"]]
  :main ^:skip-aot hyperphor.ontogeny.core
  :source-paths ["src/clj" "src/cljc" "src/cljs"]
  :resource-paths ["resources"]
  :target-path "target/%s"
  :clean-targets ^{:protect false} [".shadow-cljs" "resources/public/cljs-out" "target" "shadow-cljs.edn"]
  :uberjar-name "ontogeny-standalone.jar"
  :profiles {:uberjar {:aot :all
                        :omit-source true
                        ;; NOTE omitting javac/compile silently breaks :aot -- see eli's identical note
                        :prep-tasks [["shadow" "release" "app"] "javac" "compile"]
                        :resource-paths ["resources"]
                        :jvm-opts ["-Dclojure.compiler.direct-linking=true"]}}
  :shadow-cljs {:lein true
                :builds
                {:app {:target :browser
                       :compiler-options {:infer-externs true}
                       :output-dir "resources/public/cljs-out"
                       :asset-path "/cljs-out"
                       :modules {:dev-main {:entries [hyperphor.ontogeny.frontend.core]}}}}})
