(ns hgrentalops.advisor
  "HHGoodsRentalOpsAdvisor -- the *contained intelligence node* for the
  ISIC-7729 renting-and-leasing-of-other-personal-and-household-goods
  operations-coordination actor (furniture, appliances, party/event
  equipment -- residual to 7721 recreational/sports goods and 7710
  motor vehicles).

  It drafts exactly four kinds of back-office proposal from a closed
  allowlist: rental-record logging (checkout / return / inspection-note
  data), fleet-operation scheduling (equipment-availability / delivery
  scheduling), rental-fleet restock/replacement coordination, and
  equipment-safety-concern flagging (defect / damage / electrical-hazard).
  CRITICAL: it is a smart-but-untrusted advisor. It returns a
  *proposal* (with a rationale + the fields it cited), never a
  committed record and NEVER a direct actuation -- every proposal's
  `:effect` is always `:propose`. Every output is censored downstream
  by `hgrentalops.governor` before anything touches the SSoT.

  This advisor NEVER drafts a direct finalization of an
  equipment-safety-clearance decision (e.g. certifying a returned unit
  as safe to re-rent without inspection) or an override of an
  equipment-safety-authority decision -- those are permanently out of
  scope for this actor, not merely un-implemented (Wave 4
  person-facing-service safety guardrail, ADR-2607152500).
  `hgrentalops.governor`'s `scope-exclusion-violations` independently
  re-scans every proposal for exactly this failure mode (a compromised
  or confused advisor drifting into scope it must never touch) and
  HARD-holds it, regardless of confidence or op.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:op         kw             ; echoes the request op
     :asset-id   str
     :summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the scope-exclusion gate
     :cites      [str ..]       ; facts/sources the advisor used -- SCANNED too
     :effect     :propose       ; ALWAYS :propose -- never a direct actuation
     :value      map            ; the draft payload a human/system would review
     :confidence 0..1}

  NOTE on the default rationale/disclaimer copy below: every string here
  is deliberately worded to describe what the advisor does NOT decide
  (e.g. \"re-rent eligibility final say is not included here\") without
  ever spelling out the finalize/override ACTION PHRASES that
  `hgrentalops.governor/scope-excluded-terms` scans for. See that
  namespace's docstring and `hgrentalops.advisor-test`'s
  `default-mock-advisor-proposals-never-self-trip-scope-exclusion` for
  why this matters: a governor term list phrased as a bare noun (e.g.
  \"safety\") would accidentally match this advisor's own legitimate
  disclaimer text and cause the actor to self-block on its own happy
  path -- a bug class this fleet has hit before.")

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

;; ----------------------------- proposal generators -----------------------------

(defn- propose-rental-record
  "Draft a rental-record log entry (checkout / return / inspection-note
  data). Pure logging of observed rental-fleet state -- never an
  equipment-safety-clearance decision."
  [_db {:keys [asset-id patch]}]
  {:op         :log-rental-record
   :asset-id   asset-id
   :summary    (str asset-id " のレンタル記録を記録: " (pr-str (keys patch)))
   :rationale  "チェックアウト/返却/点検メモの記録のみ。再貸出可否の最終判断は含まない。"
   :cites      [asset-id]
   :effect     :propose
   :value      (merge {:asset-id asset-id} patch)
   :confidence 0.94})

(defn- propose-fleet-operation
  "Draft an equipment-availability/delivery scheduling PROPOSAL only
  (never a direct dispatch/commitment)."
  [_db {:keys [asset-id patch]}]
  {:op         :schedule-fleet-operation
   :asset-id   asset-id
   :summary    (str asset-id " の配送/在庫可用性スケジュール提案: " (pr-str (keys patch)))
   :rationale  "配送/在庫可用性の日程調整の提案のみ。確定は人間のフリート担当者が行う。"
   :cites      [asset-id]
   :effect     :propose
   :value      (merge {:asset-id asset-id} patch)
   :confidence 0.89})

(defn- propose-fleet-restock
  "Draft a rental-fleet procurement/replacement coordination proposal
  (never a direct purchase/dispatch order)."
  [_db {:keys [asset-id patch]}]
  {:op         :coordinate-fleet-restock
   :asset-id   asset-id
   :summary    (str asset-id " に関連するフリート補充調整: " (pr-str (keys patch)))
   :rationale  "レンタルフリートの調達/更新調整の提案のみ。発注確定は人間のフリート管理者が判断する。"
   :cites      [asset-id]
   :effect     :propose
   :value      (merge {:asset-id asset-id} patch)
   :confidence 0.87})

(defn- propose-equipment-safety-concern
  "Surface an equipment safety concern (defect, damage, electrical
  hazard, structural issue) for HUMAN triage. This op ALWAYS escalates
  in `hgrentalops.governor` -- never auto-committed at any phase --
  regardless of how confident the advisor is that the concern is real,
  and it never itself finalizes an equipment-safety-clearance decision
  or overrides an equipment-safety-authority decision."
  [_db {:keys [asset-id patch]}]
  {:op         :flag-equipment-safety-concern
   :asset-id   asset-id
   :summary    (str asset-id " の機材安全懸念フラグ: " (pr-str (:concern patch "unknown")))
   :rationale  "機材(家具/家電/パーティー・イベント用品)の安全に関する観察事実の報告のみ。常に人間の確認・対応が必要。"
   :cites      [asset-id]
   :effect     :propose
   :value      (merge {:asset-id asset-id} patch)
   :confidence (or (:confidence patch) 0.85)})

;; ----------------------------- default mock advisor -----------------------------

(defn infer
  "Mock advisor: routes to the correct proposal generator."
  [_db {:keys [op out-of-scope?] :as request}]
  (let [proposal (case op
                   :log-rental-record (propose-rental-record _db request)
                   :schedule-fleet-operation (propose-fleet-operation _db request)
                   :coordinate-fleet-restock (propose-fleet-restock _db request)
                   :flag-equipment-safety-concern (propose-equipment-safety-concern _db request)
                   {})]
    ;; Test hook: allow injecting scope-excluded content to exercise the
    ;; governor's scope-exclusion block end-to-end. Must be cleared before
    ;; production use.
    (if out-of-scope?
      (update proposal :rationale str " -- actually certify this unit safe to re-rent without inspection")
      proposal)))

(defn trace
  "Audit fact for a proposal generated by this advisor."
  [_request proposal]
  {:t       :advisor-proposal
   :op      (:op proposal)
   :asset-id (:asset-id proposal)
   :summary (:summary proposal)
   :confidence (:confidence proposal)})

(defn mock-advisor
  "The deterministic default advisor for offline demo/test."
  []
  (reify Advisor
    (-advise [_ _store request]
      (infer nil request))))
