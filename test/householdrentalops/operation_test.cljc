(ns householdrentalops.operation-test
  "Integration tests for `householdrentalops.operation/build` -- builds
  the REAL compiled `langgraph.graph` StateGraph and runs it end-to-end
  via `langgraph.graph/run*` through commit / hard-hold /
  escalate-approve / escalate-reject / phase-gate routes. Proves the
  compiled graph is real and that the audit ledger
  (`householdrentalops.store/append-ledger!`) is genuinely wired into
  the `:commit`/`:hold`/`:request-approval` nodes -- falsifiable on
  real StateGraph behavior, not hardcoded pass strings: hold-until-
  approved, ledger stays empty until commit, governor rejection blocks
  commit. Mirrors `tobaccoops.operation-test` (cloud-itonami-isic-0115)
  / `transportops.operation-test` (cloud-itonami-isic-869)."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [householdrentalops.operation :as operation]
            [householdrentalops.store :as store]))

(def operator {:actor-id "household-rental-ops-01" :role :rental-operator :phase :phase-3})

(defn- exec [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- seeded-store []
  (store/mem-store
   {:initial-scopes
    {"scope-001" {:id "scope-001" :name "North Rental Location" :verified? true}}}))

(deftest commit-path-clean-proposal
  (testing "a clean, phase-3, high-confidence release-rental-agreement request
            commits through the real compiled graph and appends exactly
            one fact to the audit ledger"
    (let [s (seeded-store)
          actor (operation/build s)
          result (exec actor "t-commit"
                       {:op :release-rental-agreement :disclosure-scope-id "scope-001"
                        :renter-id "renter-001" :tco-disclosed? true})
          state (:state result)]
      (is (= :done (:status result)))
      (is (= :commit (:disposition state)))
      (let [ledger (store/ledger s)]
        (is (= 1 (count ledger)))
        (is (= :committed (:t (first ledger))))
        (is (= :release-rental-agreement (:op (first ledger))))
        (is (= "scope-001" (:subject (first ledger))))))))

(deftest hard-hold-path-unverified-scope
  (testing "an unverified disclosure scope is a HARD, permanent governor violation
            -- the real graph routes straight to :hold (no interrupt, no
            human-approval detour) and durably records the hold fact,
            and the ledger stays empty of any :committed fact"
    (let [s (seeded-store)
          actor (operation/build s)
          result (exec actor "t-hold"
                       {:op :release-rental-agreement :disclosure-scope-id "scope-999"
                        :renter-id "renter-002" :tco-disclosed? true})
          state (:state result)]
      (is (= :done (:status result)))
      (is (= :hold (:disposition state)))
      (let [ledger (store/ledger s)]
        (is (= 1 (count ledger)))
        (is (= :governor-hold (:t (first ledger))))
        (is (some #(= :disclosure-scope-unverified (:rule %)) (:violations (first ledger))))
        (is (not-any? #(= :committed (:t %)) ledger)
            "governor rejection blocks commit -- no :committed fact ever lands")))))

(deftest hard-hold-path-condition-check-incomplete
  (testing "a delivery mission without a completed condition check is a HARD
            violation -- routes straight to :hold, no interrupt, no
            human-approval override possible"
    (let [s (seeded-store)
          actor (operation/build s)
          result (exec actor "t-hold-condition"
                       {:op :dispatch-delivery-inspection-mission
                        :disclosure-scope-id "scope-001" :rental-id "rental-001"
                        :condition-check-completed? false})
          state (:state result)]
      (is (= :done (:status result)))
      (is (= :hold (:disposition state)))
      (let [ledger (store/ledger s)]
        (is (= :governor-hold (:t (first ledger))))
        (is (some #(= :condition-check-incomplete (:rule %)) (:violations (first ledger))))))))

(deftest escalate-then-approve-commits
  (testing ":flag-disclosure-concern ALWAYS escalates -- the real graph
            GENUINELY interrupts (checkpointed) at :request-approval; the
            ledger stays completely empty until a human compliance
            officer approve! resumes the SAME compiled graph and commits
            via the graph's own :request-approval -> :commit edge"
    (let [s (seeded-store)
          actor (operation/build s)
          held (exec actor "t-escalate"
                     {:op :flag-disclosure-concern :disclosure-scope-id "scope-001"
                      :concern "更新契約書がTCO開示テンプレートと不一致の可能性"})]
      (is (= :interrupted (:status held)))
      (is (= [:request-approval] (:frontier held)))
      (is (empty? (store/ledger s))
          "hold-until-approved: not yet committed -- awaiting human sign-off, ledger stays empty until commit")
      (let [approved (g/run* actor {:approval {:status :approved :by "compliance-01"}}
                             {:thread-id "t-escalate" :resume? true})
            approved-state (:state approved)]
        (is (= :done (:status approved)))
        (is (= :commit (:disposition approved-state)))
        (let [ledger (store/ledger s)]
          (is (= 1 (count ledger)))
          (is (= :committed (:t (first ledger))))
          (is (= :flag-disclosure-concern (:op (first ledger))))
          (is (= "compliance-01" (get-in approved-state [:record :payload :approved-by]))))))))

(deftest escalate-then-reject-holds
  (testing "a human compliance officer rejecting an escalated request
            routes to :hold via the :request-approval node's own
            decision (governor rejection / human rejection both block
            commit), and durably records the rejection -- not a
            hand-rolled parallel path"
    (let [s (seeded-store)
          actor (operation/build s)
          _held (exec actor "t-reject"
                      {:op :flag-disclosure-concern :disclosure-scope-id "scope-001"
                       :concern "配送地域外の可能性"})
          rejected (g/run* actor {:approval {:status :rejected :by "compliance-01"}}
                           {:thread-id "t-reject" :resume? true})
          rejected-state (:state rejected)]
      (is (= :done (:status rejected)))
      (is (= :hold (:disposition rejected-state)))
      (let [ledger (store/ledger s)]
        (is (= 1 (count ledger)))
        (is (= :approval-rejected (:t (first ledger))))
        (is (not-any? #(= :committed (:t %)) ledger)
            "a rejected approval never reaches :commit")))))

(deftest phase-0-forces-escalation-even-when-governor-clean
  (testing "phase-0 (simulation) forces EVERY otherwise-clean commit
            through human review -- the phase gate independently
            overrides an otherwise-:commit governor verdict, proven
            against the real compiled graph. Compares two independent
            stores: a phase-3 context commits, the SAME clean proposal
            under a phase-0 context only interrupts, ledger stays empty."
    (let [request {:op :release-rental-agreement :disclosure-scope-id "scope-001"
                   :renter-id "renter-001" :tco-disclosed? true}
          s3 (seeded-store)
          actor3 (operation/build s3)
          result (exec actor3 "t-phase3" request)

          s0 (seeded-store)
          actor0 (operation/build s0)
          held (g/run* actor0 {:request request
                               :context (assoc operator :phase :phase-0)}
                       {:thread-id "t-phase0"})]
      ;; the phase-3 operator context commits (sanity check, mirrors
      ;; commit-path-clean-proposal above)
      (is (= :commit (:disposition (:state result))))
      (is (seq (store/ledger s3)))
      ;; the SAME proposal under a phase-0 context only interrupts --
      ;; no autonomous commit, ledger stays empty until a human resumes
      (is (= :interrupted (:status held)))
      (is (empty? (store/ledger s0))))))

(deftest reconciliation-record-commits-with-evidence
  (testing "a log-reconciliation-record proposal with non-empty evidence
            commits cleanly through the real compiled graph"
    (let [s (seeded-store)
          actor (operation/build s)
          result (exec actor "t-reconcile"
                       {:op :log-reconciliation-record :disclosure-scope-id "scope-001"
                        :rental-id "rental-001" :evidence ["billing-export-2026-07.csv"]
                        :amount 89.5})
          state (:state result)]
      (is (= :done (:status result)))
      (is (= :commit (:disposition state)))
      (let [ledger (store/ledger s)]
        (is (= :committed (:t (first ledger))))
        (is (= :log-reconciliation-record (:op (first ledger))))))))

(deftest reconciliation-record-hard-holds-without-evidence
  (testing "a log-reconciliation-record proposal without evidence is a
            HARD violation -- routes to :hold, never commits"
    (let [s (seeded-store)
          actor (operation/build s)
          result (exec actor "t-reconcile-no-evidence"
                       {:op :log-reconciliation-record :disclosure-scope-id "scope-001"
                        :rental-id "rental-001" :evidence []})
          state (:state result)]
      (is (= :hold (:disposition state)))
      (let [ledger (store/ledger s)]
        (is (= :governor-hold (:t (first ledger))))
        (is (some #(= :reconciliation-evidence-missing (:rule %)) (:violations (first ledger))))))))
