(ns householdrentalops.store-contract-test
  "MemStore ≡ DatomicStore parity for the Store protocol. Mirrors
  `tobaccoops.store-contract-test` (cloud-itonami-isic-0115)."
  (:require [clojure.test :refer [deftest is]]
            [householdrentalops.store :as store]))

(defn- exercise [s]
  (store/add-disclosure-scope s "scope-x" {:id "scope-x" :name "X Scope" :verified? true})
  ;; re-registering (update) exercises the identity-upsert path on
  ;; DatomicStore (:disclosure-scope/id is :db.unique/identity) the same
  ;; way MemStore's plain `assoc` re-registration does.
  (store/add-disclosure-scope s "scope-x" {:id "scope-x" :name "X Scope (renamed)" :verified? true})
  (store/append-ledger! s {:t :committed :op :release-rental-agreement :subject "scope-x"})
  (store/append-ledger! s {:t :approval-requested :op :flag-disclosure-concern :subject "scope-x"})
  {:scope  (store/registered-disclosure-scope s "scope-x")
   :absent (store/registered-disclosure-scope s "no-such-scope")
   :ledger (store/ledger s)})

(deftest mem-and-datomic-parity
  (let [mem (store/mem-store)
        dat (store/datomic-store)
        m (exercise mem)
        d (exercise dat)]
    (is (= (:scope m) (:scope d)))
    (is (= "X Scope (renamed)" (:name (:scope m))) "re-registration upserts, not forks history")
    (is (nil? (:absent m)))
    (is (nil? (:absent d)))
    (is (= 2 (count (:ledger m))))
    (is (= 2 (count (:ledger d))))
    (is (= (:ledger m) (:ledger d)))))

(deftest datomic-store-seeded-scopes
  (let [dat (store/datomic-store {:initial-scopes
                                   {"scope-y" {:id "scope-y" :name "Y Scope" :verified? true}}})]
    (is (= {:id "scope-y" :name "Y Scope" :verified? true}
           (store/registered-disclosure-scope dat "scope-y")))
    (is (empty? (store/ledger dat)))))
