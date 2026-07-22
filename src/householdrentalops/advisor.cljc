(ns householdrentalops.advisor
  "HouseholdRentalAdvisor -- the contained LLM/decision node. This
  actor's intelligence layer proposes back-office coordination actions
  (rental-purchase-agreement release/match within a verified disclosure
  scope, robotics-assisted delivery/pickup condition-inspection
  mission dispatch, rental/billing reconciliation-record logging,
  consumer-protection/disclosure concern flags) based on disclosure-
  scope state and operator/renter input. The advisor is SEALED into
  the `:advise` step of the operation flow; every proposal is routed
  through the independent Governor before committing.

  The advisor makes proposals but has NO direct authority. Proposals
  are always censored by:
    1. Governor (disclosure-scope verification, closed-op allowlist,
       TCO-disclosure/condition-check/evidence gates)
    2. Phase gate (rollout stage)
    3. Human operator (for escalated actions)

  Current implementation is a mock advisor for testing. Production
  should use langchain/Claude or similar LLM backend (same seam point
  as `tobaccoops.advisor`, cloud-itonami-isic-0115).")

;; Protocol for swappable advisor implementations
(defprotocol Advisor
  (-advise [advisor store request]
    "Given store and request, return a proposal map with :op, :effect,
    :value, :cites, :summary, :confidence (plus any op-specific top-level
    keys the Governor independently verifies, e.g.
    :tco-disclosed?/:condition-check-completed?/:evidence)."))

;; Mock advisor for testing
(defrecord MockAdvisor []
  Advisor
  (-advise [_advisor _store request]
    (let [{:keys [op disclosure-scope-id]} request]
      (case op
        :release-rental-agreement
        {:op :release-rental-agreement
         :effect :propose
         :tco-disclosed? (:tco-disclosed? request false)
         :value {:disclosure-scope-id disclosure-scope-id
                 :goods-category (:goods-category request "furniture")
                 :renter-id (:renter-id request)
                 :tco-disclosed? (:tco-disclosed? request false)}
         :cites ["renter-submitted-agreement-request"]
         :summary "Rental-purchase agreement release/match proposed within disclosure scope"
         :confidence 0.9}

        :dispatch-delivery-inspection-mission
        {:op :dispatch-delivery-inspection-mission
         :effect :propose
         :condition-check-completed? (:condition-check-completed? request false)
         :value {:disclosure-scope-id disclosure-scope-id
                 :mission-type "delivery"
                 :condition-check-completed? (:condition-check-completed? request false)
                 :rental-id (:rental-id request)}
         :cites ["robot-mission-telemetry"]
         :summary "Robotics-assisted delivery and condition-inspection mission proposed"
         :confidence 0.88}

        :dispatch-pickup-inspection-mission
        {:op :dispatch-pickup-inspection-mission
         :effect :propose
         :condition-check-completed? (:condition-check-completed? request false)
         :value {:disclosure-scope-id disclosure-scope-id
                 :mission-type "pickup"
                 :condition-check-completed? (:condition-check-completed? request false)
                 :rental-id (:rental-id request)}
         :cites ["robot-mission-telemetry"]
         :summary "Robotics-assisted pickup and condition-inspection mission proposed"
         :confidence 0.88}

        :log-reconciliation-record
        {:op :log-reconciliation-record
         :effect :propose
         :evidence (:evidence request [])
         :value {:disclosure-scope-id disclosure-scope-id
                 :rental-id (:rental-id request)
                 :evidence (:evidence request [])
                 :amount (:amount request 0)}
         :cites ["billing-system-export"]
         :summary "Rental/billing reconciliation record proposed"
         :confidence 0.85}

        :flag-disclosure-concern
        {:op :flag-disclosure-concern
         :effect :propose
         :concern (:concern request "unspecified concern")
         :value {:disclosure-scope-id disclosure-scope-id
                 :concern (:concern request "unspecified concern")
                 :recommended-action "compliance-officer-review"}
         :cites ["renter-or-operator-observation"]
         :summary "Consumer-protection/disclosure concern flagged for compliance-officer review"
         :confidence 0.8}

        ;; fallback -- unrecognized op. The Governor's closed allowlist
        ;; independently rejects this regardless of what the advisor says.
        {:op op
         :effect :propose
         :value {}
         :cites []
         :summary "Operation not recognized"
         :confidence 0.0}))))

(defn mock-advisor []
  (MockAdvisor.))

(defn trace
  "Audit trail entry for an advisor proposal. Recorded whenever a proposal
  is generated, regardless of whether it's approved."
  [request proposal]
  {:t :advisor-proposal
   :op (:op request)
   :disclosure-scope-id (:disclosure-scope-id request)
   :proposal-summary (:summary proposal)
   :confidence (:confidence proposal)})
