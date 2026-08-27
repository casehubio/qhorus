## D1: Judgment exchange identification — telemetry metadata on EVENTs

**Choice:** Judgment exchanges are identified by telemetry metadata on EVENT messages. The engine dispatches EVENTs with `toolName` matching constants from `JudgmentEventKinds` in `casehub-qhorus-api` (`YIELDED`, `RESPONDED`, `VERIFIED`, `ESCALATED`). Qhorus queries its own ledger filtering on these telemetry fields. Compile-time contract enforcement via shared constants class.
**Alternatives:**
- Dedicated channel convention (protocol tag) — binds judgment routing to channel config; judgment exchanges can span channels
- Engine EventLog SPI — tighter cross-repo contract, engine dependency from qhorus
- Content-based detection — EVENTs have null content (PP-20260608-054090); parsing message content is fragile
- Direct JudgmentLedgerEntry in casehub-ledger (R1-09) — bypasses governance layer; engine doesn't have direct ledger write access to qhorus, and shouldn't. Judgment EVENTs flowing through qhorus channels is the governance layer doing its job (recording, tamper-evidencing, attesting every exchange), not a messaging "hop." CaseLedgerEntry is engine-internal; MessageLedgerEntry is qhorus's domain.
**Rationale:** Telemetry fields on EVENT messages are the established pattern for cross-boundary metadata in qhorus (context_window_pct for CONTEXT_PRESSURE watchdog). The engine dispatches EVENTs through qhorus channels. `toolName` is the natural discriminator. Shared constants in `casehub-qhorus-api` provide compile-time contract enforcement — both engine and compliance module reference the same class, eliminating silent string mismatch (R1-08).
**Trade-offs:** Requires engine#998 to emit EVENTs matching this contract. Until then, the compliance reports return empty results. `toolName` column requires a new index (see D4) — it is not currently indexed (R1-06).
**Sources:** MessageLedgerEntry (runtime/ledger/), WatchdogEvaluationService (context_window_pct usage), V2003 routing metadata precedent
**Exploration:** quick
**Status:** revised — added JudgmentEventKinds constants (R1-08), corrected index claim (R1-06), addressed direct ledger alternative (R1-09), fixed ConnectorQhorusMeshBridge citation (R1-07)

## D2: Report shape — two new ReportType entries

**Choice:** Add `JUDGMENT_ATTRIBUTION` and `JUDGMENT_FULFILLMENT` to the `ReportType` enum. Separate service classes, model records, GraphQL queries, and REST endpoints. Existing 5 report types unchanged. Services compose with existing infrastructure (CausalGraphService, trust scores) rather than duplicating it.
**Alternatives:**
- Filters on existing reports — JUDGMENT_ATTRIBUTION uses judgmentId as entry point (not correlationId) and builds a judgment lifecycle timeline (YIELDED→RESPONDED→VERIFIED/ESCALATED) structurally distinct from the message causal graph; JUDGMENT_FULFILLMENT groups by judgmentType with evidence quality metrics — a dimension OBLIGATION doesn't have. These structural differences justify separate types over parameterisation (R1-11, R1-12).
- Single combined JUDGMENT type — mixes per-judgment attribution with per-caller aggregation, two different query patterns in one report
**Rationale:** Judgment attribution (per-judgment lifecycle chain) and fulfillment (per-type/per-caller aggregation with evidence quality) are structurally different queries. The services are thin — they query judgment-specific columns and compose with existing CausalGraphService and trust score infrastructure.
**Trade-offs:** ~10 new classes in a module with ~30 (R1-13). Mitigated by each class being focused and composing with existing infrastructure rather than duplicating it.
**Sources:** ReportType enum, AttributionReportService, ObligationReportService, CausalGraphService, ComplianceQueryResolver
**Exploration:** quick
**Status:** revised — justified structural differences from existing reports (R1-11, R1-12), noted infrastructure composition (R1-13)

## D3: Scope — full contract + implementation

**Choice:** Define the telemetry contract (constants in `casehub-qhorus-api`, telemetry field names, semantics for judgment EVENTs), implement both report services with full query logic, test with synthetic judgment EVENT data. Qhorus owns the contract — engine#998 implements against it.
**Alternatives:**
- Contract only + stubs — defers all value, no testable functionality
- Wait for engine#998 — no input on the contract from the consumption side, risk of misaligned schema
**Rationale:** The consumption side (compliance reports) defines what data it needs. Contract ownership is unambiguous: constants live in `casehub-qhorus-api`, engine depends on it (R1-15). Testing with synthetic data validates query mechanics and report composition — contract correctness is validated at integration time, not by synthetic tests (R1-16).
**Trade-offs:** If engine#998 significantly changes the judgment model, the contract may need revision. Mitigated by pre-release flexibility. Synthetic tests validate query logic, not contract alignment — acknowledged limitation.
**Depends on:** D1 (contract shape)
**Sources:** Engine#998 issue body ("Schema designed for qhorus E5 compliance export"), parent epic #410
**Exploration:** quick
**Status:** revised — clarified contract ownership (R1-15), acknowledged synthetic test limitation (R1-16)

## D4: Telemetry storage — dedicated judgment columns on MessageLedgerEntry

**Choice:** Add four dedicated nullable columns to `MessageLedgerEntry` (V2004 migration): `judgment_id UUID`, `judgment_type VARCHAR(100)`, `verification_outcome VARCHAR(20)`, `evidence_quality DOUBLE`. Add index `(tenancy_id, tool_name)` for judgment EVENT queries. All four columns included in `domainContentBytes()` for Merkle chain tamper evidence. Follows V2003 routing column precedent exactly.
**Alternatives:**
- Raw `telemetry_json TEXT` column (original D4) — general-purpose but not SQL-queryable, not type-safe, tamper evidence undecided. For three known fields, dedicated columns are strictly better (R1-20).
- Existing columns only with compound encoding — fragile, pollutes column semantics
- Compliance-local table + MessageObserver — compliance module already has JPA entities (R1-21), but the table would NOT be in the Merkle chain, losing tamper evidence for compliance-critical data (R1-18). Tamper evidence is the deciding factor.
**Rationale:** Judgment-specific fields are known at design time, not discovered at runtime. Dedicated columns are SQL-filterable (`WHERE verification_outcome = 'ACCEPTED'`), type-safe (VARCHAR/DOUBLE with constraints), schema-documented (DDL is the schema), and cheaper (no JSON parsing). V2003 added 4 routing columns — same pattern. Including in `domainContentBytes()` ensures judgment compliance data is immutable after write, satisfying EU AI Act evidence requirements (R1-18). Index on `(tenancy_id, tool_name)` makes judgment EVENT queries efficient (R1-06).
**Trade-offs:** Four more nullable columns on every MessageLedgerEntry row. Acceptable — routing columns (V2003) established this pattern and the columns are small. Compliance-local table (R1-21) would keep schema in the compliance module but sacrifices tamper evidence, which is non-negotiable for compliance data.
**Depends on:** D1 (telemetry identification approach)
**Sources:** MessageLedgerEntry.domainContentBytes() (runtime/ledger/), V2003 migration, LedgerWriteService.record()
**Exploration:** quick
**Status:** revised — replaced telemetry_json with dedicated columns (R1-20), added index (R1-06), included in domainContentBytes() (R1-18), addressed compliance-local alternative (R1-21)
