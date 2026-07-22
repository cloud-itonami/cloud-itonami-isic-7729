(ns householdrentalops.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a registered/verified
  disclosure scope through a clean phase-3 auto-commit (agreement
  release with TCO disclosed), an always-escalate disclosure concern
  (human approves), a delivery mission missing a completed condition
  check (human rejects after escalation attempt is blocked -- see
  hard-hold example below for the un-overridable variant), and a hard
  hold (unverified disclosure scope), then prints the resulting audit
  ledger. Mirrors `tobaccoops.sim` (cloud-itonami-isic-0115)."
  (:require [langgraph.graph :as g]
            [householdrentalops.operation :as operation]
            [householdrentalops.store :as store]))

(def operator {:actor-id "household-rental-ops-01" :role :rental-operator :phase :phase-3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "household-rental-ops-01"}}
          {:thread-id tid :resume? true}))

(defn- reject! [actor tid]
  (g/run* actor {:approval {:status :rejected :by "household-rental-ops-01"}}
          {:thread-id tid :resume? true}))

(defn demo
  "Run the compiled StateGraph through a commit path, an
  escalate->approve->commit path, an escalate->reject->hold path, and a
  hard-hold path; print each result and the final audit ledger."
  []
  (let [st (store/mem-store
            {:initial-scopes
             {"scope-001"
              {:id "scope-001"
               :name "North Rental Location -- Furniture/Appliance Disclosure Scope"
               :verified? true}}})
        actor (operation/build st)]

    (println "=== Household Goods Rent-to-Own Operations Coordinator Demo ===")

    (println "\n== release-rental-agreement scope-001 (phase-3, governor-clean -> commit) ==")
    (println (exec-op actor "t1"
                      {:op :release-rental-agreement :disclosure-scope-id "scope-001"
                       :goods-category "furniture" :renter-id "renter-001"
                       :tco-disclosed? true}
                      operator))

    (println "\n== flag-disclosure-concern scope-001 (ALWAYS escalates -- compliance officer approves) ==")
    (let [r (exec-op actor "t2"
                     {:op :flag-disclosure-concern :disclosure-scope-id "scope-001"
                      :concern "更新契約書がTCO開示テンプレートと不一致の可能性"}
                     operator)]
      (println r)
      (println "-- compliance officer approves --")
      (println (approve! actor "t2")))

    (println "\n== dispatch-pickup-inspection-mission scope-001 without a completed condition check (HARD hold -- no interrupt) ==")
    (println (exec-op actor "t3"
                      {:op :dispatch-pickup-inspection-mission :disclosure-scope-id "scope-001"
                       :rental-id "rental-001" :condition-check-completed? false}
                      operator))

    (println "\n== flag-disclosure-concern scope-001, second case (ALWAYS escalates -- compliance officer rejects) ==")
    (let [r (exec-op actor "t4"
                     {:op :flag-disclosure-concern :disclosure-scope-id "scope-001"
                      :concern "配送地域外の可能性"}
                     operator)]
      (println r)
      (println "-- compliance officer rejects --")
      (println (reject! actor "t4")))

    (println "\n== log-reconciliation-record scope-999 (unverified disclosure scope -> HARD hold, no interrupt) ==")
    (println (exec-op actor "t5"
                      {:op :log-reconciliation-record :disclosure-scope-id "scope-999"
                       :rental-id "rental-002" :evidence ["billing-export-2026-07.csv"]}
                      operator))

    (println "\n== audit ledger ==")
    (doseq [f (store/ledger st)] (println f))

    {:ledger (store/ledger st)}))

(defn -main
  "clojure -M:run entrypoint."
  [& _args]
  (demo))

(comment
  ;; In a real REPL:
  (demo)
  )
