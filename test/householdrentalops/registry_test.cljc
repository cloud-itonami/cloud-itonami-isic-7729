(ns householdrentalops.registry-test
  (:require [clojure.test :refer [deftest is testing]]
            [householdrentalops.registry :as registry]))

(deftest tco-not-disclosed-test
  (testing "TCO disclosed (true) is sufficient"
    (is (false? (registry/tco-not-disclosed? true))))

  (testing "TCO not disclosed (false) is a violation"
    (is (true? (registry/tco-not-disclosed? false))))

  (testing "Missing/nil TCO-disclosed flag is a violation -- never assumed true"
    (is (true? (registry/tco-not-disclosed? nil))))

  (testing "A truthy-but-not-boolean value is not accepted as disclosure"
    (is (true? (registry/tco-not-disclosed? "yes")))))

(deftest condition-check-incomplete-test
  (testing "Completed condition check (true) is sufficient"
    (is (false? (registry/condition-check-incomplete? true))))

  (testing "Incomplete condition check (false) is a violation"
    (is (true? (registry/condition-check-incomplete? false))))

  (testing "Missing/nil condition-check flag is a violation -- never assumed complete"
    (is (true? (registry/condition-check-incomplete? nil)))))

(deftest evidence-missing-test
  (testing "nil evidence is missing"
    (is (true? (registry/evidence-missing? nil))))

  (testing "Empty string evidence is missing"
    (is (true? (registry/evidence-missing? ""))))

  (testing "Blank string evidence is missing"
    (is (true? (registry/evidence-missing? "   "))))

  (testing "Empty collection evidence is missing"
    (is (true? (registry/evidence-missing? [])))
    (is (true? (registry/evidence-missing? '()))))

  (testing "Non-empty string evidence is present"
    (is (false? (registry/evidence-missing? "billing-export-2026-07.csv"))))

  (testing "Non-empty collection evidence is present"
    (is (false? (registry/evidence-missing? ["billing-export-2026-07.csv"])))))

(deftest confidence-below-floor-test
  (testing "Confidence above floor"
    (is (false? (registry/confidence-below-floor? 0.9 0.7))))

  (testing "Confidence at floor (inclusive, not below)"
    (is (false? (registry/confidence-below-floor? 0.7 0.7))))

  (testing "Confidence below floor"
    (is (true? (registry/confidence-below-floor? 0.5 0.7)))))
