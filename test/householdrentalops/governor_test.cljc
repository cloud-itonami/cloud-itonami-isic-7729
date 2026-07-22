(ns householdrentalops.governor-test
  (:require [clojure.test :refer [deftest is testing]]
            [householdrentalops.governor :as gov]
            [householdrentalops.store :as store]))

(def verified-scope {:id "scope-001" :name "North Rental Location" :verified? true})
(def unverified-scope {:id "scope-002" :name "Pending Location" :verified? false})

(deftest hard-violations-no-scope-id
  (testing "Hard violation: missing disclosure-scope-id"
    (let [req {}
          prop {:op :release-rental-agreement :effect :propose :tco-disclosed? true}
          s (store/mem-store)
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (seq (:violations verdict)))
      (is (some #(= :disclosure-scope-unverified (:rule %)) (:violations verdict))))))

(deftest hard-violations-unregistered-scope
  (testing "Hard violation: scope-id present but not registered"
    (let [req {:disclosure-scope-id "scope-001"}
          prop {:op :release-rental-agreement :effect :propose :tco-disclosed? true}
          s (store/mem-store)
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (some #(= :disclosure-scope-unverified (:rule %)) (:violations verdict))))))

(deftest hard-violations-registered-but-unverified-scope
  (testing "Hard violation: scope is registered but :verified? is false -- registration alone is not enough"
    (let [s (store/mem-store {:initial-scopes {"scope-002" unverified-scope}})
          req {:disclosure-scope-id "scope-002"}
          prop {:op :release-rental-agreement :effect :propose :tco-disclosed? true}
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (some #(= :disclosure-scope-unverified (:rule %)) (:violations verdict))))))

(deftest hard-violations-effect-not-propose
  (testing "Hard violation: effect is not :propose"
    (let [s (store/mem-store {:initial-scopes {"scope-001" verified-scope}})
          req {:disclosure-scope-id "scope-001"}
          prop {:op :release-rental-agreement :effect :execute :tco-disclosed? true}
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (some #(= :no-execution (:rule %)) (:violations verdict))))))

(deftest hard-violations-finalize-disclosure-scope-verification-blocked
  (testing "Hard violation: finalizing a disclosure-scope verification decision is permanently blocked"
    (let [s (store/mem-store {:initial-scopes {"scope-001" verified-scope}})
          req {:disclosure-scope-id "scope-001"}
          prop {:op :finalize-disclosure-scope-verification :effect :propose}
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (some #(= :decision-authority-blocked (:rule %)) (:violations verdict))))))

(deftest hard-violations-override-condition-check-blocked
  (testing "Hard violation: overriding/waiving a required condition check is permanently blocked"
    (let [s (store/mem-store {:initial-scopes {"scope-001" verified-scope}})
          req {:disclosure-scope-id "scope-001"}
          prop {:op :override-condition-check :effect :propose}
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (some #(= :decision-authority-blocked (:rule %)) (:violations verdict))))))

(deftest hard-violations-unknown-op
  (testing "Hard violation: op outside the closed allowlist"
    (let [s (store/mem-store {:initial-scopes {"scope-001" verified-scope}})
          req {:disclosure-scope-id "scope-001"}
          prop {:op :cancel-rental-agreement :effect :propose}
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (some #(= :op-not-allowed (:rule %)) (:violations verdict))))))

(deftest hard-violations-tco-not-disclosed
  (testing "Hard violation: release-rental-agreement without confirmed TCO disclosure"
    (let [s (store/mem-store {:initial-scopes {"scope-001" verified-scope}})
          req {:disclosure-scope-id "scope-001"}
          prop {:op :release-rental-agreement :effect :propose :tco-disclosed? false}
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (some #(= :tco-disclosure-missing (:rule %)) (:violations verdict)))))

  (testing "Missing :tco-disclosed? key is also a violation (never assumed true)"
    (let [s (store/mem-store {:initial-scopes {"scope-001" verified-scope}})
          req {:disclosure-scope-id "scope-001"}
          prop {:op :release-rental-agreement :effect :propose}
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (some #(= :tco-disclosure-missing (:rule %)) (:violations verdict))))))

(deftest hard-violations-condition-check-incomplete
  (testing "Hard violation: delivery mission without a completed condition check"
    (let [s (store/mem-store {:initial-scopes {"scope-001" verified-scope}})
          req {:disclosure-scope-id "scope-001"}
          prop {:op :dispatch-delivery-inspection-mission :effect :propose
                :condition-check-completed? false}
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (some #(= :condition-check-incomplete (:rule %)) (:violations verdict)))))

  (testing "Hard violation: pickup mission without a completed condition check"
    (let [s (store/mem-store {:initial-scopes {"scope-001" verified-scope}})
          req {:disclosure-scope-id "scope-001"}
          prop {:op :dispatch-pickup-inspection-mission :effect :propose
                :condition-check-completed? false}
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (some #(= :condition-check-incomplete (:rule %)) (:violations verdict))))))

(deftest hard-violations-reconciliation-evidence-missing
  (testing "Hard violation: reconciliation record without evidence"
    (let [s (store/mem-store {:initial-scopes {"scope-001" verified-scope}})
          req {:disclosure-scope-id "scope-001"}
          prop {:op :log-reconciliation-record :effect :propose :evidence []}
          verdict (gov/check req nil prop s)]
      (is (:hard? verdict))
      (is (some #(= :reconciliation-evidence-missing (:rule %)) (:violations verdict))))))

(deftest clean-proposal-passes
  (testing "A clean, well-formed release-rental-agreement proposal has no hard violations"
    (let [s (store/mem-store {:initial-scopes {"scope-001" verified-scope}})
          req {:disclosure-scope-id "scope-001"}
          prop {:op :release-rental-agreement :effect :propose :tco-disclosed? true
                :confidence 0.9}
          verdict (gov/check req nil prop s)]
      (is (false? (:hard? verdict)))
      (is (empty? (:violations verdict)))
      (is (:ok? verdict)))))

(deftest always-escalate-flag-disclosure-concern
  (testing "flag-disclosure-concern ALWAYS escalates even when otherwise clean and high-confidence"
    (let [s (store/mem-store {:initial-scopes {"scope-001" verified-scope}})
          req {:disclosure-scope-id "scope-001"}
          prop {:op :flag-disclosure-concern :effect :propose :confidence 0.99}
          verdict (gov/check req nil prop s)]
      (is (false? (:hard? verdict)))
      (is (:escalate? verdict))
      (is (:high-stakes? verdict))
      (is (false? (:ok? verdict))))))

(deftest low-confidence-escalates
  (testing "Confidence below the floor escalates even when otherwise clean"
    (let [s (store/mem-store {:initial-scopes {"scope-001" verified-scope}})
          req {:disclosure-scope-id "scope-001"}
          prop {:op :release-rental-agreement :effect :propose :tco-disclosed? true
                :confidence 0.4}
          verdict (gov/check req nil prop s)]
      (is (false? (:hard? verdict)))
      (is (:escalate? verdict))
      (is (false? (:ok? verdict))))))
