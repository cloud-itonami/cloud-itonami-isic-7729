(ns hgrentalops.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300,
  Wave5 rollout ledger seq 6): this repo previously had NO demo page and
  no generator at all. This namespace drives the REAL actor stack
  (`hgrentalops.operation` -> `hgrentalops.governor` -> `hgrentalops.store`)
  through a scenario adapted from this repo's own `hgrentalops.sim` demo
  driver (`clojure -M:dev:run`, confirmed to run correctly against the
  real seeded household-goods rental fleet directory before this file
  was written -- unlike `cloud-itonami-isic-851`'s `schoolops.sim`, this
  repo's own sim driver uses ids that DO match `hgrentalops.store/demo-data`
  (unit-1/unit-2/unit-3), so it was safe to reuse rather than author from
  scratch), trimmed to a representative subset (clean auto-commits, an
  always-escalate safety-concern flag that is approved, and three
  distinct HARD-hold reasons: an unverified rental asset, a proposal
  whose own `:effect` was not `:propose`, and a proposal that drifted
  into the permanently-excluded equipment-safety-clearance-finalization
  scope) and rendered deterministically -- no invented numbers, no
  timestamps in the page content, byte-identical across reruns against
  the same seed (verified by diffing two consecutive runs).

  This actor's scope is deliberately narrow -- COORDINATION ONLY. It
  never finalizes an equipment-safety-clearance decision and never
  overrides an equipment-safety-authority decision (ADR-2607152500); no
  such op exists in `hgrentalops.governor/allowed-ops` to invoke here.

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [clojure.string :as str]
            [hgrentalops.store :as store]
            [hgrentalops.operation :as op]
            [hgrentalops.advisor :as advisor]
            [langgraph.graph :as g]))

(def ^:private operator
  {:actor-id "op-1" :actor-role :fleet-manager :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}}
          {:thread-id tid :resume? true}))

(defn run-demo!
  "Runs a fresh seeded store through a scenario mixing every disposition
  this actor can reach: unit-1 clears a rental-record log, a
  fleet-operation delivery-scheduling proposal, and a fleet-restock
  coordination proposal (all auto-commit clean at phase 3); unit-1's
  equipment-safety-concern flag ALWAYS escalates (per
  `always-escalate-ops`) even though clean, and is approved by a human;
  unit-3 (registered but NOT `:verified?` in the seed data) HARD-holds
  on `:asset-unverified` -- never reaches a human; a
  `:schedule-fleet-operation` proposal whose advisor drifted into
  claiming a direct actuation (`:effect :commit` instead of `:propose`)
  HARD-holds on `:effect-not-propose` -- never reaches a human; a
  `:log-rental-record` proposal whose advisor drifted into the
  permanently-excluded re-rent-without-inspection scope HARD-holds on
  `:scope-excluded` -- never reaches a human. Returns the resulting
  store -- every field read by `render` below is real governor/store
  output, not a hand-typed copy."
  []
  (let [db (store/seed-db)
        actor (op/build db)]
    (exec! actor "u1-log" {:op :log-rental-record :asset-id "unit-1"
                            :patch {:renter "Jane Doe" :checkout "2026-07-14" :days 3}})

    (exec! actor "u1-schedule" {:op :schedule-fleet-operation :asset-id "unit-1"
                                 :patch {:item "delivery re-scheduling" :window "2026-07-20 09:00-12:00"}})

    (exec! actor "u1-restock" {:op :coordinate-fleet-restock :asset-id "unit-1"
                                :patch {:item "replacement sofa cushions" :quantity 2 :estimated-cost 250}})

    (exec! actor "u1-safety" {:op :flag-equipment-safety-concern :asset-id "unit-1"
                               :patch {:concern "frame joint loose, wobbles under load" :confidence 0.92}})
    (approve! actor "u1-safety")

    (exec! actor "u3-log" {:op :log-rental-record :asset-id "unit-3"
                            :patch {:renter "unknown"}})

    (let [actor-direct (op/build db {:advisor (reify advisor/Advisor
                                                (-advise [_ _ req]
                                                  (assoc (advisor/infer nil req) :effect :commit)))})]
      (exec! actor-direct "u1-direct" {:op :schedule-fleet-operation :asset-id "unit-1"
                                        :patch {:item "annual safety recertification delivery slot"}}))

    (exec! actor "u1-scope" {:op :log-rental-record :asset-id "unit-1"
                              :out-of-scope? true :patch {}})
    db))

;; ----------------------------- rendering -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- last-fact-for [ledger asset-id]
  (last (filter #(= (:asset-id %) asset-id) ledger)))

(defn- status-cell [ledger asset-id]
  (let [f (last-fact-for ledger asset-id)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (= :committed (:t f)) "<span class=\"ok\">committed</span>"
      (= :approval-granted (:t f)) "<span class=\"ok\">approved &amp; committed</span>"
      (= :governor-hold (:t f))
      (let [rule (-> f :violations first :rule)]
        (case rule
          :asset-unverified "<span class=\"critical\">HARD hold &middot; unverified asset</span>"
          :effect-not-propose "<span class=\"critical\">HARD hold &middot; effect not propose</span>"
          :scope-excluded "<span class=\"critical\">HARD hold &middot; scope-excluded</span>"
          (str "<span class=\"critical\">HARD hold &middot; " (esc (name (or rule :unknown))) "</span>")))
      (= :approval-requested (:t f)) "<span class=\"warn\">awaiting approval</span>"
      :else "<span class=\"muted\">in progress</span>")))

(defn- unit-row [ledger {:keys [asset-id name registered? verified?]}]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc asset-id) (esc name)
          (if (and registered? verified?) "<span class=\"ok\">registered &amp; verified</span>"
              (if registered? "<span class=\"warn\">registered, unverified</span>"
                  "<span class=\"err\">unregistered</span>"))
          (status-cell ledger asset-id)))

(defn- ledger-row [{:keys [t op asset-id disposition basis]}]
  (format "        <tr><td>%s</td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc (name t)) (esc (name (or op :n-a))) (esc asset-id)
          (esc (or (some->> basis (map name) (str/join ", ")) (some-> disposition name) ""))))

(def ^:private action-gate-rows
  ;; Static description of this actor's own closed op contract (README
  ;; `Ops` table, `hgrentalops.governor`/`hgrentalops.phase`) --
  ;; documentation of fixed behavior, not runtime telemetry, so it is
  ;; legitimately hand-described rather than derived from a live run.
  ["        <tr><td><code>:log-rental-record</code></td><td><span class=\"ok\">phase-3 auto when clean</span></td></tr>"
   "        <tr><td><code>:schedule-fleet-operation</code></td><td><span class=\"ok\">phase-3 auto when clean</span></td></tr>"
   "        <tr><td><code>:coordinate-fleet-restock</code></td><td><span class=\"ok\">phase-3 auto when clean under cost threshold</span> &middot; <span class=\"warn\">ALWAYS human approval over threshold</span></td></tr>"
   "        <tr><td><code>:flag-equipment-safety-concern</code></td><td><span class=\"warn\">ALWAYS human approval &middot; never auto, any phase</span></td></tr>"])

(defn render
  "Renders the full operator-console.html document from a store `db`
  that has already run `run-demo!` (or any other real scenario)."
  [db]
  (let [ledger (vec (store/ledger db))
        units (store/all-assets db)
        unit-rows (str/join "\n" (map (partial unit-row ledger) units))
        ledger-rows (str/join "\n" (map ledger-row ledger))]
    (str
     "<html><head><meta charset=\"utf-8\"><title>cloud-itonami-isic-7729 &middot; household-goods rental fleet coordination</title><style>\n"
     "table { width: 100%; border-collapse: collapse; font-size: 14px; }\n"
     ".ok { color: #137a3f; }\n"
     "body { font-family: system-ui,-apple-system,sans-serif; margin: 0; color: #1a1a1a; background: #fafafa; }\n"
     "header.bar { display: flex; align-items: center; gap: 12px; padding: 12px 20px; background: #fff; border-bottom: 1px solid #e5e5e5; }\n"
     "th, td { text-align: left; padding: 8px 10px; border-bottom: 1px solid #f0f0f0; }\n"
     "h2 { margin-top: 0; font-size: 15px; }\n"
     ".warn { color: #b25c00; background: #fff8e1; padding: 2px 6px; border-radius: 4px; }\n"
     "main { max-width: 980px; margin: 24px auto; padding: 0 20px; }\n"
     "header.bar h1 { font-size: 18px; margin: 0; font-weight: 600; }\n"
     ".muted { color: #888; font-size: 13px; }\n"
     ".critical { color: #fff; background: #b3261e; padding: 2px 6px; border-radius: 4px; font-weight: 600; }\n"
     ".card { background: #fff; border: 1px solid #e5e5e5; border-radius: 8px; padding: 16px; margin-bottom: 16px; }\n"
     ".err { color: #b3261e; background: #fbe9e7; padding: 2px 6px; border-radius: 4px; }\n"
     "th { font-weight: 600; color: #555; font-size: 12px; text-transform: uppercase; letter-spacing: 0.04em; }\n"
     "header.bar .badge { margin-left: auto; font-size: 12px; color: #666; }\n"
     "code { font-size: 12px; background: #f4f4f4; padding: 1px 4px; border-radius: 3px; }\n"
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Household-goods rental fleet coordination (ISIC 7729) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · never finalizes equipment-safety-clearance</span>\n"
     "</header>\n"
     "<main>\n"
     "  <section class=\"card\">\n"
     "    <h2>Rental fleet units</h2>\n"
     "    <p class=\"muted\">Demo snapshot — build-time-generated from <code>hgrentalops.store</code> via <code>hgrentalops.render-html</code> (<code>clojure -M:dev:render-html</code>), regenerated nightly.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Unit</th><th>Name</th><th>Registration status</th><th>Last op status</th></tr></thead>\n"
     "      <tbody>\n"
     unit-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Action gate (HHGoodsRentalGovernor)</h2>\n"
     "    <p class=\"muted\">HARD holds cannot be overridden. This actor's scope is coordination only — it never finalizes an equipment-safety-clearance decision and never overrides an equipment-safety-authority decision.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Gate</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" action-gate-rows) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (this run)</h2>\n"
     "    <p class=\"muted\">Append-only decision-fact log — every proposal, hold and commit this scenario produced.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Fact</th><th>Op</th><th>Unit</th><th>Basis</th></tr></thead>\n"
     "      <tbody>\n"
     ledger-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "</main>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        db (run-demo!)
        html (render db)]
    (spit out html)
    (println "wrote" out "(" (count (store/ledger db)) "ledger facts,"
             (count (store/coordination-log db)) "committed coordination records )")))
