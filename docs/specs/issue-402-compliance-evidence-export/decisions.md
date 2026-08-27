## D1: Trust score history — first-class time series in ledger

**Choice:** Add `actor_trust_score_snapshot` table in casehub-ledger with scheduled snapshot writes after each TrustScoreCalculator run
**Alternatives:**
- Derive trajectories by replaying attestations over time windows — expensive O(attestations × windows), imprecise (attestation events, not computed scores)
- Record trust score changes as `LedgerEntry` records with `TrustScoreSupplement` — gains Merkle tamper-evidence and PROV-DM export, but trust score recomputations fire on every attestation (via `IncrementalTrustUpdateObserver`), generating high-volume entries; subject scope (per-channel) doesn't naturally fit actor-scoped scores
- CDC (change data capture) on existing `ActorTrustScore` table — infrastructure overhead (Debezium or similar), operational complexity disproportionate to the use case
- Time-partitioned history table — functionally equivalent to snapshot table with added partition management complexity
**Rationale:** Small schema cost enables incident investigation ("what was trust at time T?"), degradation detection, federated trust context, and dashboard rendering without recomputation. The computed score is a weighted function of ALL attestations including credibility — replay can't reconstruct it cheaply.
**Trade-offs:** Requires a ledger issue and ledger schema change. Storage grows linearly with actors × snapshot frequency. Schema ownership: ledger designs the snapshot table for general use, not reverse-engineered from qhorus's specific needs — the qhorus issue provides the requirements, ledger owns the schema.
**Sources:** TrustExportService (ledger federation), TrustScoreCalculator, ActorTrustScoreRepository, IncrementalTrustUpdateObserver
**Exploration:** quick
**Status:** revised — expanded alternatives (LedgerEntry-based, CDC, time-partitioned), added schema ownership note

## D2: PROV-DM export — correlation-scoped causal graph complement to ledger's per-subject export

**Choice:** PROV-DM export maps qhorus correlation-scoped causal graphs (from `CausalGraphService.buildGraph(correlationId)`) to W3C provenance vocabulary. This is complementary to `LedgerProvExportService`'s per-subject PROV-JSON-LD export — different scope, same vocabulary. Ledger exports a subject's complete audit trail; qhorus exports a correlation's cross-channel message flow with attribution, timing, and outcome.
**Alternatives:**
- Internal-only custom format — simpler but locks out external tool ecosystem
- External-only PROV-DM with separate internal format — duplication
- Thin adapter delegating to `LedgerProvExportService` with correlation-based entry selection — doesn't work because `CausalGraphService` produces graph-shaped output (nodes, edges, depth) that `LedgerProvSerializer`'s sequential chain model can't represent
**Rationale:** `CausalGraphService.buildGraph()` produces cross-channel causal trees — parent→child edges via `causedByEntryId`, across multiple channels, with timing and outcome derivation. `LedgerProvExportService.exportSubject()` produces sequential entry chains within a single subject. These are structurally different graphs requiring separate PROV-DM mappers. Both use the same W3C PROV vocabulary for interoperability.
**Trade-offs:** Two PROV-DM export paths exist in the platform. Mitigated by clear scope boundaries (per-subject vs per-correlation) and shared vocabulary.
**Sources:** CausalGraphService, LedgerProvExportService, LedgerProvSerializer, W3C PROV-DM spec, EU AI Act Article 12 (record-keeping)
**Exploration:** quick
**Status:** revised — explicitly scoped as correlation-based complement to LedgerProvExportService

## D3: PDF deferred — JSON/CSV/HTML first

**Choice:** Deliver JSON, CSV, and structured HTML for the first cut. PDF rendering (OpenHTMLtoPDF) deferred to a separate issue.
**Alternatives:**
- Include PDF in first cut — adds a heavyweight dependency (OpenHTMLtoPDF + flying-saucer) and HTML template design work
- Skip PDF entirely — leaves out a format regulators often expect
**Rationale:** JSON/CSV covers machine consumption and data exchange. HTML covers human-readable reports (browsers print to PDF natively). Dedicated PDF rendering is polish, not a compliance gate.
**Trade-offs:** Regulators who expect a "download PDF" button won't have it immediately. Browser-printed PDFs are a stopgap, not a regulatory substitute — they lack digital signatures, document metadata, accessibility compliance (PDF/A), and eIDAS qualified electronic seals that some regulators require for non-repudiation. The deferred PDF issue must address these requirements.
**Sources:** Issue #402 scope list
**Exploration:** quick
**Status:** revised — trade-off statement updated to acknowledge browser PDF limitations for regulatory submission

## D4: Report storage — SharedData artefact body + compliance_report metadata index

**Choice:** Generated report bodies stored as SharedData artefacts with claim/release lifecycle. A `compliance_report` metadata table (`id`, `report_type`, `tenant_id`, `generated_at`, `schedule_id`, `artefact_id FK`, `schema_version`) provides domain-specific query access. On-demand reports return data directly (no storage needed); scheduled reports store artefact + metadata.
**Alternatives:**
- Filesystem — simpler but non-portable across nodes, no lifecycle management
- SharedData only (no metadata table) — `DataQuery` supports only `createdBy` and `complete` filters; no date/type/tenant filtering. Consumers couldn't query "all weekly reports from Q1 2026" or "latest report of each type" without fragile key-encoding conventions
- Dedicated table replacing SharedData — unnecessary duplication of blob storage, claim/release lifecycle, and GC eligibility that SharedData already provides
**Rationale:** Reports are artefacts (SharedData body) AND compliance records (metadata index). The metadata table is small, domain-appropriate, and supports the query patterns regulators need: by type, by date range, by tenant, latest-per-type. The artefact body still benefits from SharedData's claim/release lifecycle and GC.
**Trade-offs:** Two storage locations per report (metadata + artefact body). Mitigated by FK relationship and atomic writes within the same transaction.
**Depends on:** D5 (module placement)
**Sources:** DataService, SharedData, ArtefactClaim, DataQuery, DataStore
**Exploration:** quick
**Status:** revised — added compliance_report metadata table for domain-specific query patterns

## D5: New optional module `compliance-report/` — qhorus as composition point

**Choice:** New Maven module `compliance-report/` alongside webhook-observer/, notification-bridge/, etc. Contains report composition services, REST resources, CSV/HTML renderers, scheduled generation, and GraphQL resolvers (discovered via Jandex when on classpath — not in the existing `graphql/` module). SPI types (`CompliancePostureProvider`, `CompliancePosture`, `PostureEntry`, `PostureStatus`) live in `casehub-qhorus-api/spi/compliance/` per `consumer-spi-placement` protocol (R2-01). This module is the composition point for cross-cutting compliance data: it aggregates from `LedgerComplianceReportService` (tamper-evident decision records, stable dependency — casehub-ledger already in qhorus's dependency graph), compliance posture data via `CompliancePostureProvider` SPI (optional — `@DefaultBean NoOp` when casehub-ops absent, see D9), and qhorus's own services (`CausalGraphService`, commitment tracking, protocol enforcement).
**Alternatives:**
- In runtime module — simpler build but runtime already large, not all consumers need compliance reports
- Report SPI in platform-api + per-repo contributors — follows `ActorStateContributor` pattern but fragments the compliance narrative. A compliance report tells a governance story: this agent did X because Y, with trust Z, attested by W. Splitting that story across independent contributors produces disjoint sections that can't cross-reference or maintain narrative coherence. The SPI pattern works for ActorState (each contributor adds independent state dimensions) but not for compliance reporting (where sections are causally linked)
- Each repo independently produces partial reports — no composition strategy, consumers manually stitch together ledger, qhorus, and ops reports
**Rationale:** Compliance reporting is optional — not every qhorus consumer is regulated. Follows the established optional-module pattern (classpath activation). Qhorus is the natural composition point because the governance narrative is its domain: agent communication, protocol enforcement, trust scoring, and causal attribution are all qhorus concerns. It delegates to ledger and ops for their data via their service APIs. The 5 report types in #402 are CaseHub-derived categories based on the platform's data model, not a claim to exhaustive EU AI Act Art.12 coverage — Art.12 compliance validation is a spec-level task, not a module-placement decision.
**Trade-offs:** New module adds build complexity. Qhorus compliance-report module has a compile-scope dependency on casehub-ledger (stable, already in the dependency graph). Compliance posture data from casehub-ops is accessed via SPI with `@DefaultBean NoOp` — no Maven dependency on casehub-ops required.
**Depends on:** D4 (storage choice affects module dependencies)
**Sources:** webhook-observer/, notification-bridge/, graphql/ module patterns, LedgerComplianceReportService, CompliancePostureProvider SPI (D9), ActorStateContributor (platform-api pattern)
**Exploration:** quick
**Status:** revised — GraphQL resolvers in compliance-report/ (R1-02), SPI types in api/spi/compliance/ (R2-01), ops dependency via SPI with @DefaultBean NoOp

## D6: Scheduled reports via DigestSchedule + CDI event notification

**Choice:** Compose with platform's existing `DigestSchedule` (sealed: Interval, DailyAt, WeeklyAt) for timing. Report schedule config stores a `DigestSchedule` variant. Sweep uses `isFlushDue(lastRunAt, lastRunAt, now)` — both `oldestPending` and `lastFlush` positions receive `lastRunAt`, giving correct behavior for all three variants: DailyAt/WeeklyAt use position 2 (lastFlush), Interval uses position 1 (oldestPending → checks `lastRunAt + period <= now`). When due: generate report → store as SharedData artefact + metadata → fire `ComplianceReportGeneratedEvent` CDI event. Notification delivery is decoupled from report generation — observers (notification-bridge, webhook-observer, etc.) handle delivery through their own channels.
**Alternatives:**
- Custom `@Scheduled` sweep with home-grown schedule table — reinvents DigestSchedule's timing semantics
- Local scheduler SPI in qhorus-api — duplicates platform capability
- Quarkus Scheduler programmatic API — implementation coupling, no durability
- Direct `NotificationDeliverer` from compliance-report module — couples report generation to notification delivery; report generation shouldn't know about notification channels
**Rationale:** DigestSchedule already encodes the timing semantics correctly. The `isFlushDue(lastRunAt, lastRunAt, now)` convention works uniformly across all three variants. CDI event for delivery notification follows the established platform event pattern and avoids coupling report generation to notification channel choice. Note: ADR-0018's prohibition applies to qhorus cursor-based message delivery, not to platform notifications about generated artefacts — but the CDI event pattern avoids the concern entirely by decoupling report generation from delivery.
**Trade-offs:** `DigestSchedule.Interval`'s `oldestPending` parameter has notification-specific semantics; passing `lastRunAt` in that position is a semantic workaround that works correctly but misuses the parameter name. Documented as a known convention. CDI event requires an observer for notifications to be delivered — this is correct: notification delivery is the consumer's concern, not the report generator's.
**Depends on:** D5 (module placement)
**Sources:** DigestSchedule.java, DigestBuffer.java, ADR-0018 (non-applicability documented)
**Exploration:** quick
**Status:** revised — fixed calling convention, replaced NotificationDeliverer with CDI event, documented ADR-0018 boundary

## D7: Triple exposure — REST + GraphQL + MCP via @McpDomain

**Choice:** Report services exposed via three surfaces: REST resources in `compliance-report/` (content negotiation via Accept header for JSON/CSV/HTML), GraphQL resolvers in `compliance-report/` (discovered via Jandex when module is on classpath — not in the existing `graphql/` module, per R1-02), MCP via `@McpDomain("qhorus")` on GraphQL resolvers (automatic MCP tool generation from GraphQL schema). REST paths follow CausalGraphResource pattern: `/api/compliance/{report-type}` with query params for filtering. Schedule CRUD at `/api/compliance/schedules`. Stored reports retrieved via `/api/compliance/reports/{id}` with `Accept` header content negotiation (re-rendered from stored JSON).
**Alternatives:**
- REST-only — misses the GraphQL consumers and MCP agent access
- Build separate MCP tools manually — redundant when @McpDomain gives MCP tool generation from GraphQL for free
**Rationale:** The platform's MCP mechanism is `@McpDomain` applied to `@GraphQLApi` resolver classes (e.g., `QhorusQueryResolver`, `LedgerQueryResolver`). This generates MCP tools from the GraphQL schema. GraphQL resolvers in `compliance-report/` use `@McpDomain("qhorus")` — multiple classes across modules share the same domain value, and the MCP tool generator groups by domain, not by module. REST is a separate manually-written surface for content-negotiated responses (JSON/CSV/HTML).
**Trade-offs:** GraphQL schema splits across modules when compliance-report/ is on classpath. Acceptable — Jandex discovery handles the composition, and the schema is additive (compliance queries appear when the module is present, absent when it's not).
**Depends on:** D5 (module placement)
**Sources:** QhorusQueryResolver @McpDomain("qhorus") (graphql/), QhorusMutationResolver @McpDomain("qhorus") (graphql/), CausalGraphResource (runtime/api/), McpDomain annotation (platform-api)
**Exploration:** quick
**Status:** revised — resolvers moved to compliance-report/ (R1-02), corrected MCP mechanism to @McpDomain on GraphQL resolvers, retrieval uses Accept header re-rendering (R2-03)

## D8: PROV-DM serialization as PROV-JSON-LD

**Choice:** PROV-JSON-LD (JSON-LD with PROV vocabulary and `@context`) as the serialization format for PROV-DM export, aligning with the existing `LedgerProvSerializer.toProvJsonLd()` output format.
**Alternatives:**
- PROV-JSON (W3C Note, flat JSON without `@context`) — structurally different from existing ledger export; audit tools consuming both would need two parsers
- PROV-N (human-readable text) — niche, fewer tools consume it
- PROV-O (RDF/OWL) — requires RDF libraries (Apache Jena), heavyweight for the use case
**Rationale:** `LedgerProvSerializer` already produces PROV-JSON-LD with `@context` containing `prov`, `ledger`, and `xsd` namespace IRIs. The qhorus PROV-DM export should use the same format so consumers can process both with a single parser. JSON-LD is also the richer format: IRI-based identifiers enable cross-referencing between per-subject (ledger) and per-correlation (qhorus) PROV documents via shared entity IRIs.
**Trade-offs:** Slightly more verbose than flat PROV-JSON due to `@context` and IRI structure. Mitigated by the interoperability benefit — one format across the platform.
**Sources:** LedgerProvSerializer.toProvJsonLd(), W3C PROV-JSON spec, W3C JSON-LD spec, CausalGraphService
**Exploration:** quick
**Status:** revised — changed from PROV-JSON to PROV-JSON-LD for consistency with existing LedgerProvSerializer

## D9: Report composition architecture — qhorus aggregates cross-cutting compliance data via SPI boundaries

**Choice:** The `compliance-report/` module in qhorus is the single composition point for compliance evidence reports. Data source access uses differentiated dependency strategies based on stability:
- **casehub-ledger** (stable, shipped): direct compile-scope dependency on `LedgerComplianceReportService` for tamper-evident decision records and Merkle root anchors. casehub-ledger is already in qhorus's dependency graph (`runtime/pom.xml` line 103).
- **casehub-ops** (research-stage): accessed via `CompliancePostureProvider` SPI defined in `casehub-qhorus-api/spi/compliance/` (per `consumer-spi-placement` protocol — R2-01), with a `@DefaultBean NoOp` in `compliance-report/posture/` returning empty posture data. casehub-ops depends on `casehub-qhorus-api` only (lightweight) and provides an `@Alternative @Priority(1)` implementation delegating to `CompliancePostureService` — activates only when casehub-ops is on the classpath. No Maven dependency from qhorus on casehub-ops.
- **qhorus-internal**: direct access to `CausalGraphService` for attribution chains, commitment tracking for obligation fulfillment, protocol enforcement for violation logs.
**Alternatives:**
- ComplianceReportContributor SPI in platform-api (like ActorStateContributor) — each repo contributes a section; a platform-level aggregator composes them. Clean separation but produces disjoint report sections. Compliance reports need causal cross-references between sections (e.g., "trust score dropped because protocol violation triggered attestation review") that contributors can't provide independently
- Hard dependency on casehub-ops — couples a shipped feature (EU AI Act compliance export) to a research-stage module. If ops compliance API changes during research iteration, qhorus compliance-report breaks. Contradicts the optional-module pattern: "an optional module that mandates a research-stage dependency is not optional"
- Dedicated compliance-reporting repo — adds a repo with no domain logic of its own, pure aggregation. Over-abstracted for 5 well-defined report types
- Each subsystem (ledger, ops, qhorus) exposes its own compliance endpoint — consumers manually aggregate. No composition, no cross-referencing, no coherent narrative
**Rationale:** Qhorus is the governance domain — agent communication, protocol enforcement, trust, and causal attribution are its concerns. A compliance evidence report tells a governance story that needs data from multiple sources but is authored from qhorus's perspective. The composition point should be where the domain expertise lives. The `@DefaultBean` + `@Alternative` pattern is the established platform mechanism for optional data sources — used by `NoOpCapabilityHealth` (engine), `NoOpActorTrustScoreRepository` (ledger), `NoOpChannelActivityBroadcaster` (qhorus), `StoredCommitmentAttestationPolicy` (qhorus), and 293+ other references across the platform.
**Trade-offs:** Compliance reports omit posture data when casehub-ops is absent. This is correct — a consumer who hasn't deployed the ops compliance module has no posture data to report. The report gracefully degrades rather than failing. If a fourth compliance data source emerges, it follows the same SPI pattern.
**Sources:** LedgerComplianceReportService, CompliancePostureProvider SPI, CausalGraphService, NoOpCapabilityHealth @DefaultBean pattern (engine), NoOpActorTrustScoreRepository @DefaultBean pattern (ledger), capability-ownership.md (ops compliance = Research project)
**Exploration:** quick (surfaced by review, refined by R2-01)
**Status:** revised — replaced hard ops dependency with CompliancePostureProvider SPI + @DefaultBean NoOp (R2-01)

## D10: Report versioning — immutable snapshots with schema version

**Choice:** Generated reports are immutable snapshots. Each report carries a `schemaVersion` field (in both the report body and the `compliance_report` metadata table). Historical reports are never re-generated or mutated — they represent the compliance state at generation time. When report format changes (new fields, new EU AI Act delegated acts), a new schema version is introduced. Consumers can filter by schema version.
**Alternatives:**
- Re-generatable reports (store parameters, regenerate on demand) — loses the point-in-time snapshot, risky for regulatory evidence where the exact report submitted to a regulator must be preserved
- Versioned templates with migration — over-engineered for JSON/CSV/HTML reports
**Rationale:** Compliance reports are evidence. An auditor asking "what did your Q1 2026 report say?" expects the exact document, not a regenerated version with updated logic. Immutable snapshots with schema versioning satisfy both audit requirements and format evolution.
**Trade-offs:** Old-format reports persist indefinitely. Not a problem — GC eligibility via SharedData claim/release handles storage lifecycle.
**Sources:** LedgerComplianceReportService (already produces immutable ComplianceReport objects), EU AI Act Art.12
**Exploration:** quick (surfaced by review)
**Status:** captured

## D11: Report integrity — Merkle root reference from ledger

**Choice:** Each generated compliance report includes the current Merkle root hash for all subjects referenced in the report, obtained from `LedgerVerificationService.treeRoot()`. This provides tamper-evidence: if any ledger entry used in the report is later modified, the Merkle root won't match. This follows the pattern established by `LedgerComplianceReportService.reportForActor()` which already includes `merkleRootAtGeneration`.
**Alternatives:**
- Digital signature on report documents (eIDAS qualified seal, PKI) — correct for external submission but heavyweight for internal compliance records; deferred alongside PDF (D3)
- No integrity guarantee — unacceptable for regulatory evidence
- Full Merkle proof per entry — excessive; root hash is sufficient for tamper-detection (detail proofs on demand via `LedgerVerificationService.verifyEntry()`)
**Rationale:** The ledger already provides Merkle tamper-evidence. Reports that reference ledger data should anchor to the Merkle state at generation time. Digital signing for external submission is deferred with PDF rendering — the Merkle root provides internal integrity immediately.
**Trade-offs:** Reports reference a Merkle root that may change as new entries are appended (roots are per-subject). The stored root proves no entries were MODIFIED since report generation — new entries are expected.
**Sources:** LedgerVerificationService, LedgerComplianceReportService.reportForActor(), merkleRootAtGeneration pattern
**Exploration:** quick (surfaced by review)
**Status:** captured

## D12: Tenant isolation for scheduled reports

**Choice:** Compliance report schedules are per-tenant, not per-user. The `compliance_report_schedule` configuration table includes `tenant_id` as a required field. The sweep loop queries due schedules partitioned by tenant, ensuring one tenant's schedule cadence doesn't affect another's. This differs from `DigestFlushScheduler`'s per-user model because compliance reporting is an organizational obligation, not a user preference.
**Alternatives:**
- Per-user schedules (DigestFlushScheduler model) — doesn't match compliance use case; EU AI Act Art.12 obligations are per-deployer (organization), not per-individual
- Global schedules (one schedule shared across tenants) — violates tenant isolation; different tenants may have different reporting cadences based on their regulatory environment
**Rationale:** Compliance obligations are organizational. A tenant's compliance officer configures the schedule; all reports for that tenant follow it. The sweep checks `isFlushDue` per schedule row, each scoped to its tenant.
**Trade-offs:** More schedule rows than a global model. Negligible storage — schedule rows are metadata, not data.
**Sources:** DigestSchedule, multi-tenancy architecture
**Exploration:** quick (surfaced by review)
**Status:** captured
