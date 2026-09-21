(ns hyperphor.ontogeny.schema-gen-test
  (:require [hyperphor.ontogeny.schema-gen :as sg]
            [clojure.test :refer [deftest is]]))

;;; merge-icons is pure (no LLM call), so it's tested directly against a
;;; hand-built proposal map rather than a real LLM response.
(def schema
  {:kinds {:venue {:icon "📍" :fields {}}
           :person {:fields {}}
           :album {:fields {}}}})

(def proposed
  {:venue "🎪"                  ; already iconed -- should NOT overwrite
   :person "🧑"                 ; valid -- should apply
   :album ""                    ; empty string -- should reject
   :nonexistent-kind "🎷"})     ; not in schema -- should be dropped

(deftest merge-icons-is-conservative
  (let [merged (sg/merge-icons schema proposed)]
    (is (= "📍" (get-in merged [:kinds :venue :icon])) "existing icon preserved")
    (is (= "🧑" (get-in merged [:kinds :person :icon])) "valid new icon applied")
    (is (nil? (get-in merged [:kinds :album :icon])) "empty-string icon rejected")
    (is (not (contains? (:kinds merged) :nonexistent-kind)) "unknown kind not added")))
