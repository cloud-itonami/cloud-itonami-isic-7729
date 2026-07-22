(ns householdrentalops.registry
  "Pure validation functions for community household-goods rent-to-own
  operations. These are called by the Governor to independently verify
  proposal parameters -- the advisor's confidence is NOT sufficient to
  override these checks. Mirrors `tobaccoops.registry`
  (cloud-itonami-isic-0115) in shape, adapted to this actor's own
  structural invariants (TCO-disclosure confirmation, condition-check
  completion, reconciliation evidence) rather than crop/leaf-grade
  checks. Every predicate here is a structural/internal plausibility
  check, independently re-derived by the Governor from the proposal's
  own fields -- never a citation of an external regulatory standard
  beyond what this repo's README/docs already state (Rental-Purchase
  Agreement Act / FTC total-cost-of-ownership disclosure guidance,
  already present in README.md before this implementation)."
  (:require [clojure.string :as str]))

(defn tco-not-disclosed?
  "A `:release-rental-agreement` proposal must independently confirm
  total-cost-of-ownership disclosure was completed for the renter
  before the agreement may release (README: 'total-cost-of-ownership-
  disclosure management'; business-model.md: 'agreements cannot be
  released outside verified disclosure scope'). `true` unless the
  proposal's `:tco-disclosed?` is literally `true` -- never trusts an
  absent or truthy-but-not-boolean value as disclosure having
  happened."
  [tco-disclosed?]
  (not (true? tco-disclosed?)))

(defn condition-check-incomplete?
  "A `:dispatch-delivery-inspection-mission` /
  `:dispatch-pickup-inspection-mission` proposal must independently
  confirm the condition inspection leg completed (operator-guide.md:
  'governor gate on every robot action before dispatch'; business-
  model.md Trust Controls: 'a delivery/pickup without a completed
  condition check' is a safety-critical action requiring human
  sign-off / must never auto-commit). `true` unless the proposal's
  `:condition-check-completed?` is literally `true`."
  [condition-check-completed?]
  (not (true? condition-check-completed?)))

(defn evidence-missing?
  "A `:log-reconciliation-record` proposal must cite non-empty
  evidence (business-model.md Trust Controls: 'reconciliation records
  require verified evidence'; operator-guide.md: 'evidence-backed
  reconciliation records'). `true` when evidence is nil, an empty
  string, or an empty collection -- a present-but-empty value is not a
  real citation."
  [evidence]
  (cond
    (nil? evidence) true
    (string? evidence) (str/blank? evidence)
    (coll? evidence) (empty? evidence)
    :else false))

(defn confidence-below-floor?
  "Independently verify a proposal's stated confidence against the
  Governor's confidence floor."
  [confidence floor]
  (< confidence floor))
