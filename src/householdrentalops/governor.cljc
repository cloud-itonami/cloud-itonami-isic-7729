(ns householdrentalops.governor
  "Household Rental Governor -- the independent compliance layer that
  earns the HouseholdRentalAdvisor the right to commit. The LLM has no
  notion of:
    - Whether the disclosure scope a proposal targets is actually
      registered AND independently verified
    - Whether a proposal is a real actuation (`:effect :propose` only --
      this actor NEVER directly releases a rental agreement, dispatches
      a delivery/pickup robot, or finalizes a disclosure-scope
      verification decision)
    - Whether an op is inside this actor's closed coordination allowlist
    - Whether a `:release-rental-agreement` proposal actually confirms
      total-cost-of-ownership disclosure completed
    - Whether a delivery/pickup mission proposal actually confirms its
      condition-inspection leg completed
    - Whether a logged reconciliation record actually cites evidence

  This MUST be a separate system able to *reject* a proposal and fall
  back to HOLD.

  This actor is a back-office HOUSEHOLD-GOODS RENTAL OPERATIONS
  COORDINATOR only -- finalizing a disclosure-scope verification
  decision and overriding/waiving a required condition check are
  categorically outside its authority (operator/compliance-officer
  exclusive). The Governor enforces that boundary structurally, not by
  trusting the advisor's judgment. This mirrors
  `vehiclerentalops.governor` (cloud-itonami-isic-7710)'s driver-
  eligibility-override / vehicle-safety-clearance exclusion pattern,
  adapted to household-goods rent-to-own's own scope boundary.

  CRITICAL: Any proposal to flag a consumer-protection/disclosure
  concern ALWAYS escalates to a human (compliance officer/operator) for
  final sign-off. The LLM's confidence is never sufficient for a
  consumer-protection decision.

  Hard violations (always HOLD, no override, permanent):
    1. Disclosure scope not registered/verified (scope-id missing,
       unknown to Store, or on file but not marked `:verified?`) --
       applies to EVERY op, per business-model.md Trust Controls:
       'agreements cannot be released outside verified disclosure
       scope' (this Governor extends that invariant to every proposal
       type this actor may make, not release-agreement alone -- the
       same 'no proposal acts outside a verified authority unit'
       discipline `tobaccoops.governor`'s field-registration check
       uses)
    2. Proposal `:effect` is not `:propose` (no direct execution, ever)
    3. Op is `:finalize-disclosure-scope-verification` or
       `:override-condition-check` -- finalizing disclosure-scope
       compliance verification and overriding/waiving a required
       condition check are PERMANENTLY blocked regardless of proposal
       content or confidence
    4. Op is outside the closed proposal-op allowlist
    5. `:release-rental-agreement` without a confirmed
       total-cost-of-ownership disclosure (`:tco-disclosed?` not
       `true`) -- business-model.md: 'total-cost-of-ownership-
       disclosure management'
    6. `:dispatch-delivery-inspection-mission` /
       `:dispatch-pickup-inspection-mission` without a completed
       condition check (`:condition-check-completed?` not `true`) --
       operator-guide.md: 'a delivery/pickup without a completed
       condition check' is safety-critical
    7. `:log-reconciliation-record` without verified evidence
       (`:evidence` missing/empty) -- business-model.md: 'reconciliation
       records require verified evidence'

  Soft gates (always escalate for human):
    - `:flag-disclosure-concern` -- ALWAYS escalates
    - Low confidence

  This design mirrors `tobaccoops.governor` (cloud-itonami-isic-0115)
  in structure but specializes household-goods rent-to-own back-office
  coordination concerns (disclosure-scope verification, closed op
  allowlist, TCO-disclosure/condition-check/reconciliation-evidence
  gates) rather than tobacco-farm concerns."
  (:require [householdrentalops.registry :as registry]
            [householdrentalops.store :as store]))

(def confidence-floor 0.7)

(def blocked-ops
  "Finalizing a disclosure-scope compliance verification decision, and
  overriding/waiving a required condition check, sit outside this
  actor's coordination-only authority. ALWAYS a hard, permanent block
  -- never escalate, never override, regardless of confidence or
  cites."
  #{:finalize-disclosure-scope-verification
    :override-condition-check})

(def known-ops
  "The closed allowlist of proposal ops this actor may make -- all
  `:effect :propose` (see ADR domain design)."
  #{:release-rental-agreement :dispatch-delivery-inspection-mission
    :dispatch-pickup-inspection-mission :log-reconciliation-record
    :flag-disclosure-concern})

(def always-escalate-ops
  "Ops that ALWAYS require human sign-off even when the Governor finds
  no hard violation and confidence is high. Flagging a disclosure
  concern is never something this actor resolves autonomously."
  #{:flag-disclosure-concern})

(def all-recognized-ops
  "known-ops (allowed to proceed) union blocked-ops (recognized but
  permanently forbidden). Anything outside this union is an unknown op
  -- a HARD violation, not a silent no-op."
  (into known-ops blocked-ops))

;; ----------------------------- checks -----------------------------

(defn- disclosure-scope-violations
  "A proposal referencing an unregistered or unverified disclosure
  scope is a HARD violation -- never act on behalf of a scope this
  actor cannot independently verify. Applies uniformly to every op,
  not release-rental-agreement alone."
  [{:keys [disclosure-scope-id]} st]
  (let [scope (store/registered-disclosure-scope st disclosure-scope-id)]
    (when-not (and scope (true? (:verified? scope)))
      [{:rule :disclosure-scope-unverified
        :detail (str "disclosure-scope-id " (pr-str disclosure-scope-id) " は登録済み・検証済みの開示範囲として確認できない -- 検証済み開示範囲外の提案は進められない")}])))

(defn- execution-violations
  "This actor never executes directly. Any proposal whose `:effect` isn't
  `:propose` is a HARD violation, independent of what op it claims."
  [proposal]
  (when-not (= :propose (:effect proposal))
    [{:rule :no-execution
      :detail "提案の :effect は :propose でなければならない -- governor は直接実行/作動を許可しない"}]))

(defn- decision-authority-violations
  "Finalizing a disclosure-scope compliance verification decision, and
  overriding/waiving a required condition check, are a HARD, permanent
  block -- compliance-verification and condition-check authority
  remains exclusively human (operator/compliance officer)."
  [proposal]
  (when (contains? blocked-ops (:op proposal))
    [{:rule :decision-authority-blocked
      :detail (str (:op proposal) " は開示範囲のコンプライアンス検証確定、または必須コンディションチェックの上書き/免除であり、恒久的にブロックされる -- オペレーター/コンプライアンス担当者の専権事項")}]))

(defn- unknown-op-violations
  "Enforce the closed proposal-op allowlist independently of the
  advisor's claim -- an op outside `all-recognized-ops` is a HARD
  violation, never a silent pass-through."
  [proposal]
  (when-not (contains? all-recognized-ops (:op proposal))
    [{:rule :op-not-allowed
      :detail (str (:op proposal) " はクローズドallowlist外の操作")}]))

(defn- tco-disclosure-violations
  "For `:release-rental-agreement`, INDEPENDENTLY verify
  total-cost-of-ownership disclosure was confirmed via
  `registry/tco-not-disclosed?`."
  [proposal]
  (when (and (= :release-rental-agreement (:op proposal))
             (registry/tco-not-disclosed? (:tco-disclosed? proposal)))
    [{:rule :tco-disclosure-missing
      :detail "total-cost-of-ownership（総保有コスト）開示の完了が確認できない -- 契約リリース提案は進められない"}]))

(defn- condition-check-violations
  "For `:dispatch-delivery-inspection-mission` /
  `:dispatch-pickup-inspection-mission`, INDEPENDENTLY verify the
  condition-inspection leg completed via
  `registry/condition-check-incomplete?`."
  [proposal]
  (when (and (contains? #{:dispatch-delivery-inspection-mission
                           :dispatch-pickup-inspection-mission}
                         (:op proposal))
             (registry/condition-check-incomplete? (:condition-check-completed? proposal)))
    [{:rule :condition-check-incomplete
      :detail (str (:op proposal) " はコンディションチェック（状態確認）完了が確認できない -- 配送/引取ミッションは進められない")}]))

(defn- reconciliation-evidence-violations
  "For `:log-reconciliation-record`, INDEPENDENTLY verify cited
  evidence is present and non-empty via
  `registry/evidence-missing?`."
  [proposal]
  (when (and (= :log-reconciliation-record (:op proposal))
             (registry/evidence-missing? (:evidence proposal)))
    [{:rule :reconciliation-evidence-missing
      :detail "根拠となるエビデンスが確認できない -- 未検証の消込（reconciliation）記録は進められない"}]))

(defn check
  "Censors a HouseholdRentalAdvisor proposal against the Governor rules.
  Returns {:ok? bool :violations [..] :confidence c :escalate?
  :high-stakes? :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (disclosure-scope-violations request st)
                           (execution-violations proposal)
                           (decision-authority-violations proposal)
                           (unknown-op-violations proposal)
                           (tco-disclosure-violations proposal)
                           (condition-check-violations proposal)
                           (reconciliation-evidence-violations proposal)))
        conf (:confidence proposal 0.0)
        low? (registry/confidence-below-floor? conf confidence-floor)
        always-escalate? (contains? always-escalate-ops (:op proposal))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not always-escalate?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? always-escalate?))
     :high-stakes? (boolean always-escalate?)}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:disclosure-scope-id request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
