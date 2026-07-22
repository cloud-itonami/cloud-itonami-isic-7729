(ns householdrentalops.phase-test
  (:require [clojure.test :refer [deftest is testing]]
            [householdrentalops.phase :as phase]))

(deftest verdict-to-disposition
  (testing "Clean verdict -> commit"
    (let [verdict {:escalate? false :hard? false}
          disposition (phase/verdict->disposition verdict)]
      (is (= :commit disposition))))

  (testing "Escalate verdict -> escalate"
    (let [verdict {:escalate? true :hard? false}
          disposition (phase/verdict->disposition verdict)]
      (is (= :escalate disposition))))

  (testing "Hard violation -> hold"
    (let [verdict {:escalate? false :hard? true}
          disposition (phase/verdict->disposition verdict)]
      (is (= :hold disposition)))))

(deftest phase-0-gate
  (testing "A would-be commit is forced to escalate in phase-0 (no autonomous commits during simulation)"
    (let [request {:op :release-rental-agreement}
          result (phase/gate :phase-0 request :commit)]
      (is (= :escalate (:disposition result)))
      (is (= :phase-0-simulation-only (:reason result)))))

  (testing "Hold passes through unchanged in phase-0"
    (let [request {:op :finalize-disclosure-scope-verification}
          result (phase/gate :phase-0 request :hold)]
      (is (= :hold (:disposition result)))
      (is (nil? (:reason result)))))

  (testing "Escalate passes through unchanged in phase-0"
    (let [request {:op :flag-disclosure-concern}
          result (phase/gate :phase-0 request :escalate)]
      (is (= :escalate (:disposition result)))
      (is (nil? (:reason result))))))

(deftest phase-1-gate
  (testing "Always-escalate op forces escalate even if the Governor is clean"
    (let [request {:op :flag-disclosure-concern}
          result (phase/gate :phase-1 request :commit)]
      (is (= :escalate (:disposition result)))
      (is (= :phase-1-always-escalate (:reason result)))))

  (testing "Routine op commits in phase-1 when clean"
    (let [request {:op :release-rental-agreement}
          result (phase/gate :phase-1 request :commit)]
      (is (= :commit (:disposition result)))
      (is (nil? (:reason result)))))

  (testing "Hold passes through unchanged in phase-1"
    (let [request {:op :release-rental-agreement}
          result (phase/gate :phase-1 request :hold)]
      (is (= :hold (:disposition result))))))

(deftest phase-2-gate
  (testing "Disposition passed through unchanged"
    (let [request {:op :anything}]
      (is (= :commit (:disposition (phase/gate :phase-2 request :commit))))
      (is (= :hold (:disposition (phase/gate :phase-2 request :hold))))
      (is (= :escalate (:disposition (phase/gate :phase-2 request :escalate)))))))

(deftest phase-3-gate
  (testing "Disposition passed through unchanged (full autonomy -- governor verdict authoritative)"
    (let [request {:op :anything}]
      (is (= :commit (:disposition (phase/gate :phase-3 request :commit))))
      (is (= :hold (:disposition (phase/gate :phase-3 request :hold))))
      (is (= :escalate (:disposition (phase/gate :phase-3 request :escalate)))))))

(deftest unknown-phase-defaults-to-hold
  (testing "An unrecognized phase is a conservative hold, not a silent pass-through"
    (let [request {:op :release-rental-agreement}
          result (phase/gate :phase-99 request :commit)]
      (is (= :hold (:disposition result)))
      (is (= :unknown-phase (:reason result))))))

(deftest default-phase-is-phase-0
  (testing "Default phase is the most conservative (simulation only)"
    (is (= :phase-0 phase/default-phase))))
