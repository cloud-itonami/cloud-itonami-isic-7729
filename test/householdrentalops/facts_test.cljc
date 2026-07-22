(ns householdrentalops.facts-test
  (:require [clojure.test :refer [deftest is testing]]
            [householdrentalops.facts :as facts]))

(deftest goods-categories-reference-set
  (testing "Household-goods categories from the README scope note are present"
    (is (contains? facts/goods-categories "furniture"))
    (is (contains? facts/goods-categories "major-appliance"))
    (is (contains? facts/goods-categories "small-appliance"))
    (is (contains? facts/goods-categories "consumer-electronics")))

  (testing "Out-of-scope categories (vehicles, recreational goods, tools) are absent"
    (is (not (contains? facts/goods-categories "motor-vehicle")))
    (is (not (contains? facts/goods-categories "recreational-equipment")))
    (is (not (contains? facts/goods-categories "construction-tool")))))

(deftest goods-category-known-test
  (testing "Known category"
    (is (true? (facts/goods-category-known? "furniture"))))
  (testing "Unknown category -- informational only, not a validated enum"
    (is (false? (facts/goods-category-known? "unknown-category")))))

(deftest mission-types-reference-set
  (testing "Delivery and pickup mission legs are present"
    (is (contains? facts/mission-types "delivery"))
    (is (contains? facts/mission-types "pickup")))

  (testing "Not a validated enum -- an unlisted mission type is simply absent"
    (is (not (contains? facts/mission-types "installation")))))

(deftest default-confidence-floor-value
  (testing "Default confidence floor matches the governor's own floor"
    (is (= 0.7 facts/default-confidence-floor))))
