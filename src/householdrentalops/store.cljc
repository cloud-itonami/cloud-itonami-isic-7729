(ns householdrentalops.store
  "SSoT for the community household-goods rent-to-own operations
  coordinator, behind a `Store` protocol so the backend is a swap, not
  a rewrite -- the same seam every cloud-itonami actor in this fleet
  uses (mirrors `tobaccoops.store`, cloud-itonami-isic-0115;
  `cerealops.store`, cloud-itonami-isic-0111):

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/householdrentalops/store_contract_test.cljc).

  A verified rental-purchase-agreement/disclosure scope is the minimal
  unit of authority: a household-goods rental request must reference a
  registered AND verified disclosure scope before ANY proposal
  referencing it can be considered by the Governor (see
  `householdrentalops.governor`'s `disclosure-scope-violations`
  invariant -- 'agreements cannot be released outside verified
  disclosure scope', business-model.md Trust Controls). Disclosure-
  scope data is opaque to this namespace -- callers/backends decide
  what a scope record contains (goods category, TCO disclosure
  template, rental location, etc); this Store only answers 'is this
  disclosure-scope-id registered AND verified, and if so what's on
  file'. Because the scope payload shape is intentionally open,
  `DatomicStore` stores it as a single opaque EDN-blob attribute
  (`:disclosure-scope/payload`, via `langchain-store.core`'s
  `enc`/`dec*`) rather than expanding it into per-key Datomic
  attributes -- the same blob convention every sibling DatomicStore
  already uses for its own opaque payloads.

  The append-only audit ledger (`ledger`/`append-ledger!`) is this
  actor's core commit/hold record: `householdrentalops.operation`'s
  `:commit`/`:hold` graph nodes append every committed/held/
  approval-rejected decision fact here, so a disclosure scope's
  operating history (every `:release-rental-agreement` /
  `:dispatch-delivery-inspection-mission` /
  `:dispatch-pickup-inspection-mission` / `:log-reconciliation-record`
  / `:flag-disclosure-concern` decision) is always a query over an
  immutable log -- the same discipline every sibling actor's ledger
  provides. The ledger stays append-only on every backend."
  (:require [langchain.db :as d]
            [langchain-store.core :as ls]))

(defprotocol Store
  (registered-disclosure-scope [store scope-id]
    "Retrieve a registered rental-purchase-agreement/disclosure-scope
    record by ID. Returns nil if scope-id is nil or not registered.")
  (add-disclosure-scope [store scope-id scope-data]
    "Register or update a disclosure scope in the store. Used by
    tests, simulation, and operator onboarding.")
  (ledger [store]
    "The append-only audit ledger: every committed/held/approval-rejected
    decision fact, in append order.")
  (append-ledger! [store fact]
    "Append one immutable decision fact to the ledger. Returns fact."))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [scopes ledger-atom]
  Store
  (registered-disclosure-scope [_store scope-id]
    (when scope-id
      (get @scopes scope-id)))
  (add-disclosure-scope [_store scope-id scope-data]
    (swap! scopes assoc scope-id scope-data)
    scope-data)
  (ledger [_store] @ledger-atom)
  (append-ledger! [_store fact]
    (swap! ledger-atom conj fact)
    fact))

(defn mem-store
  "Create an in-memory store. `initial-scopes` is an optional map of
  scope-id -> disclosure-scope-record."
  [& [{:keys [initial-scopes] :or {initial-scopes {}}}]]
  (MemStore. (atom initial-scopes) (atom [])))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  `:disclosure-scope/payload` is stored as an EDN string blob (via
  `langchain-store.core`) so `langchain.db` doesn't try to expand an
  opaque, caller-defined scope record into sub-entities. The identity-
  schema builder, EDN-blob codec and seq-keyed event-log read/append are
  the shared kotoba-lang/langchain-store machinery (ADR-2607141600) --
  the seam ~190 actors hand-roll; this store keeps only its domain
  wiring."
  (ls/identity-schema [:disclosure-scope/id :ledger/seq]))

(defrecord DatomicStore [conn]
  Store
  (registered-disclosure-scope [_store scope-id]
    (when scope-id
      (ls/dec* (d/q '[:find ?p .
                      :in $ ?sid
                      :where [?e :disclosure-scope/id ?sid] [?e :disclosure-scope/payload ?p]]
                    (d/db conn) scope-id))))
  (add-disclosure-scope [_store scope-id scope-data]
    (d/transact! conn [{:disclosure-scope/id scope-id
                         :disclosure-scope/payload (ls/enc scope-data)}])
    scope-data)
  (ledger [_store] (ls/read-stream conn :ledger/seq :ledger/fact))
  (append-ledger! [store fact]
    (ls/append-blob! conn :ledger/seq :ledger/fact (count (ledger store)) fact)
    fact))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `initial-scopes`
  (scope-id -> disclosure-scope-record); empty when omitted."
  [& [{:keys [initial-scopes] :or {initial-scopes {}}}]]
  (let [s (->DatomicStore (d/create-conn schema))]
    (doseq [[scope-id scope-data] initial-scopes]
      (add-disclosure-scope s scope-id scope-data))
    s))
