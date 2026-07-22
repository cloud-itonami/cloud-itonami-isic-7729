(ns householdrentalops.store-test
  (:require [clojure.test :refer [deftest is testing]]
            [householdrentalops.store :as store]))

(deftest mem-store-creation
  (testing "Create empty store"
    (let [st (store/mem-store)]
      (is (some? st))
      (is (satisfies? store/Store st))))

  (testing "Create store with initial scopes"
    (let [scopes {"scope-001" {:id "scope-001" :name "North Rental Location" :verified? true}}
          st (store/mem-store {:initial-scopes scopes})]
      (is (some? st))
      (is (satisfies? store/Store st)))))

(deftest registered-disclosure-scope-retrieval
  (testing "Retrieve existing scope"
    (let [scope {:id "scope-001" :name "North Rental Location" :verified? true}
          st (store/mem-store {:initial-scopes {"scope-001" scope}})]
      (is (= scope (store/registered-disclosure-scope st "scope-001")))))

  (testing "Retrieve non-existent scope"
    (let [st (store/mem-store)]
      (is (nil? (store/registered-disclosure-scope st "no-such-scope")))))

  (testing "nil scope-id returns nil (never falls through to a default)"
    (let [st (store/mem-store {:initial-scopes {"scope-001" {:id "scope-001"}}})]
      (is (nil? (store/registered-disclosure-scope st nil))))))

(deftest add-disclosure-scope-test
  (testing "Register a new disclosure scope"
    (let [st (store/mem-store)
          scope-data {:id "scope-002" :name "New Scope" :verified? true}
          result (store/add-disclosure-scope st "scope-002" scope-data)]
      (is (= scope-data result))
      (is (= scope-data (store/registered-disclosure-scope st "scope-002")))))

  (testing "Update an existing disclosure scope"
    (let [st (store/mem-store {:initial-scopes {"scope-001" {:id "scope-001" :verified? false}}})
          updated {:id "scope-001" :name "Renamed Scope" :verified? true}
          result (store/add-disclosure-scope st "scope-001" updated)]
      (is (= updated result))
      (is (= updated (store/registered-disclosure-scope st "scope-001"))))))

(deftest ledger-append-order
  (testing "Ledger facts are appended in order"
    (let [st (store/mem-store)]
      (store/append-ledger! st {:t :committed :seq 1})
      (store/append-ledger! st {:t :governor-hold :seq 2})
      (is (= [{:t :committed :seq 1} {:t :governor-hold :seq 2}]
             (store/ledger st))))))
