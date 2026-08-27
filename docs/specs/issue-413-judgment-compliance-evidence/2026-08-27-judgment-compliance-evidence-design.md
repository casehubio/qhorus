# Design: Judgment Compliance Evidence for E5 Audit Reports

**Issue:** casehubio/qhorus#413
**Parent:** casehubio/qhorus#410 (governed yield governance)
**Cross-repo:** casehubio/engine#998 (judgment ledger events)
**Date:** 2026-08-27
**Status:** Draft

---

## Context

Epic #410 introduces governed yields — the engine yields judgment requests to callers, callers respond with evidence, the engine verifies. This exchange produces a provenance chain: what was asked, who responded, what evidence was provided, whether verification passed, and why.

The E5 compliance export module (`compliance-report/`) already provides 5 report types (Attribution, Obligation, TrustHistory, Violation, Provenance) that work from qhorus's `MessageLedgerEntry` data. None distinguish judgment exchanges from regular message flows. This work extends E5 with two judgment-specific report types that consume judgment provenance EVENTs from the engine.

Engine#998 (YIELDED/RESPONDED/VERIFIED/ESCALATED event types) has not landed yet. This design defines the telemetry contract from the consumption side — engine#998 implements against it.

---

## Telemetry Contract

The engine dispatches judgment provenance as `MessageType.EVENT` messages through qhorus channels. Four event kinds, identified by `toolName` constants defined in `JudgmentEventKinds` (casehub-qhorus-api):

| Constant | `toolName` value | When | Extracted columns |
|---|---|---|---|
| `YIELDED` | `judgment_yielded` | Engine yields judgment to a caller | `judgmentId`, `judgmentType` |
| `RESPONDED` | `judgment_responded` | Caller provides evidence | `judgmentId`, `judgmentType`, `evidenceQuality` |
| `VERIFIED` | `judgment_verified` | Engine verifies evidence | `judgmentId`, `judgmentType`, `verificationOutcome` |
| `ESCALATED` | `judgment_escalated` | Judgment reassigned to another caller | `judgmentId`, `judgmentType` |

### correlationId contract requirement

Judgment EVENTs MUST share the `correlationId` of the COMMAND message that triggered the judgment yield. This is an explicit engine contract requirement — without it, the JudgmentAttributionReport's causal graph section produces no results. The YIELDED event's `correlationId` is the link between the judgment lifecycle and the message flow.

### Existing column mapping

Existing `MessageLedgerEntry` telemetry columns carry common fields:

| Column | Judgment usage |
|---|---|
| `toolName` | Event kind discriminator (`judgment_yielded` etc.) |
| `sourceEntity` | Not used for judgment events — judgment_type stored in dedicated column |
| `contextRefs` | Not used for judgment events — judgment_id stored in dedicated column |
| `durationMs` | Latency since previous event in chain |

### New dedicated columns (V2004)

Four new nullable columns on `message_ledger_entry` for judgment-specific fields:

| Column | Type | Description |
|---|---|---|
| `judgment_id` | `UUID` | Correlates all events in one judgment exchange |
| `judgment_type` | `VARCHAR(100)` | Classification (e.g., "code_review", "quality_check") |
| `verification_outcome` | `VARCHAR(20)` | ACCEPTED / REJECTED / PARTIAL (VERIFIED events only) |
| `evidence_quality` | `DOUBLE PRECISION` | 0.0-1.0 evidence quality score (RESPONDED events only) |

All four included in `MessageLedgerEntry.domainContentBytes()` for Merkle chain tamper evidence. Backward compatibility: existing entries have all four fields null, producing an unchanged hash. See §MessageLedgerEntry Changes for the tagged suffix encoding.

### Full telemetry JSON per event kind

Fields beyond the extracted columns are carried in the telemetry JSON (available to the engine for its own use but not extracted to qhorus columns):

**YIELDED:**
```json
{
  "tool_name": "judgment_yielded",
  "judgment_id": "abc123-...",
  "judgment_type": "code_review",
  "binding_id": "capability-xyz",
  "evidence_requirements": "diff review with line-level comments"
}
```

**RESPONDED:**
```json
{
  "tool_name": "judgment_responded",
  "judgment_id": "abc123-...",
  "judgment_type": "code_review",
  "evidence_quality": 0.85,
  "evidence_summary": "reviewed 15 files, 3 issues found"
}
```

**VERIFIED:**
```json
{
  "tool_name": "judgment_verified",
  "judgment_id": "abc123-...",
  "judgment_type": "code_review",
  "verification_outcome": "ACCEPTED",
  "verification_confidence": 0.92,
  "verification_method": "automated_check"
}
```

**ESCALATED:**
```json
{
  "tool_name": "judgment_escalated",
  "judgment_id": "abc123-...",
  "judgment_type": "code_review",
  "escalation_reason": "timeout",
  "original_caller": "agent-a",
  "escalated_to": "agent-b"
}
```

### Contract constants

```java
// api/src/main/java/io/casehub/qhorus/api/judgment/JudgmentEventKinds.java
public final class JudgmentEventKinds {
    public static final String YIELDED = "judgment_yielded";
    public static final String RESPONDED = "judgment_responded";
    public static final String VERIFIED = "judgment_verified";
    public static final String ESCALATED = "judgment_escalated";
    public static final String TOOL_NAME_PREFIX = "judgment_";

    private JudgmentEventKinds() {}
}
```

Both engine and compliance module reference these constants — compile-time contract enforcement eliminates silent string mismatch.

---

## Report Types

Two new entries in `ReportType` enum: `JUDGMENT_ATTRIBUTION`, `JUDGMENT_FULFILLMENT`.

### 1. Judgment Attribution Report

Full provenance chain for a single judgment exchange. Given a `judgmentId`, builds the timeline of YIELDED→RESPONDED→VERIFIED/ESCALATED events enriched with trust scores, plus the underlying message causal graph.

```java
public record JudgmentAttributionReport(
    String judgmentId,
    String judgmentType,
    int channelCount,
    List<String> channels,
    String correlationId,
    String verificationOutcome,
    Long totalDurationMs,
    List<JudgmentEvent> events,
    List<AttributionNode> causalNodes,
    List<AttributionEdge> causalEdges,
    String merkleRoot,
    Instant generatedAt,
    int schemaVersion
) {}

public record JudgmentEvent(
    String eventKind,
    String actorId,
    Instant occurredAt,
    Double evidenceQuality,
    String verificationOutcome,
    String escalationReason,
    Double trustScoreAtTime,
    Long durationMs
) {}
```

**Data flow:**
1. Query `MessageLedgerEntryRepository.findJudgmentEvents(null, judgmentId, null, null, tenancyId)` — all EVENTs for this judgment
2. Extract `correlationId` from the YIELDED event's parent message (the COMMAND that triggered the judgment)
3. Build causal graph via `CausalGraphService.buildGraph(correlationId, limit, tenancyId)` — reuse existing infrastructure
4. Enrich judgment events with trust scores via `TrustGateService.currentScore(actorId)`
5. Composite Merkle root from referenced channels

The `JudgmentEvent` timeline is distinct from the causal graph. The timeline tracks the judgment lifecycle (YIELDED→RESPONDED→VERIFIED/ESCALATED). The causal graph tracks the underlying message flow (COMMAND→RESPONSE→DONE). Both are included — they are complementary views of the same exchange.

**Data sources:** `MessageLedgerEntryRepository` (judgment events), `CausalGraphService` (message causal graph), `TrustGateService` (trust scores), `LedgerVerificationService` (Merkle root)

### 2. Judgment Fulfillment Report

Aggregate metrics over a time range, grouped by judgment type and by caller.

```java
public record JudgmentFulfillmentReport(
    Instant from,
    Instant to,
    List<JudgmentTypeSummary> byType,
    List<CallerSummary> byCaller,
    int totalJudgments,
    int accepted,
    int rejected,
    int escalated,
    int pending,
    double overallAcceptanceRate,
    double averageResponseTimeMs,
    double averageEvidenceQuality,
    String merkleRoot,
    Instant generatedAt,
    int schemaVersion
) {}

public record JudgmentTypeSummary(
    String judgmentType,
    int total,
    int accepted,
    int rejected,
    int escalated,
    int pending,
    double acceptanceRate,
    double averageResponseTimeMs,
    double averageEvidenceQuality
) {}

public record CallerSummary(
    String actorId,
    int total,
    int accepted,
    int rejected,
    int escalated,
    int pending,
    double acceptanceRate,
    double averageResponseTimeMs,
    double averageEvidenceQuality,
    Double currentTrustScore
) {}
```

**Data flow:**
1. SQL aggregation queries on `MessageLedgerEntry` — NOT bulk loading. Three queries:
   - `countJudgmentOutcomes(from, to, tenancyId, judgmentType?, actorId?)` — `GROUP BY judgment_type, verification_outcome` for time-bounded outcomes (accepted/rejected/escalated)
   - `findPendingJudgments(tenancyId, judgmentType?, actorId?)` — YIELDED events with no VERIFIED/ESCALATED for the same `judgment_id` (unbounded — all currently open, regardless of when yielded)
   - `aggregateJudgmentMetrics(from, to, tenancyId, judgmentType?, actorId?)` — `AVG(evidence_quality)`, response time computation
2. Build per-type and per-caller summaries from aggregated results
3. Enrich caller summaries with `TrustGateService.currentScore(actorId)`
4. Optional filters: `judgmentType`, `actorId` — pushed to SQL WHERE clause

**Temporal scope:**
- `accepted`, `rejected`, `escalated` — bounded to `[from, to]` based on VERIFIED/ESCALATED event `occurredAt`
- `pending` — all currently open judgments (YIELDED with no VERIFIED/ESCALATED for the same `judgment_id`), NOT bounded to `[from, to]`. Follows the existing `ObligationReport.stillOpen` pattern — open judgments persist regardless of when they were yielded.
- `averageResponseTimeMs` — computed from `occurredAt` timestamps: mean of (RESPONDED.occurredAt - YIELDED.occurredAt) per judgment chain. Does NOT use `durationMs`, which only captures the last hop in escalated chains (ESCALATED→RESPONDED latency, not YIELDED→RESPONDED total elapsed).
- `averageEvidenceQuality` — mean of `evidenceQuality` on RESPONDED events (nulls excluded)

**Data sources:** `MessageLedgerEntryRepository` (judgment events), `TrustGateService` (trust scores), `LedgerVerificationService` (Merkle root)

---

## Database Migration

**V2004** in `runtime/src/main/resources/db/qhorus/migration/`:

```sql
-- Judgment compliance columns on message_ledger_entry
ALTER TABLE message_ledger_entry ADD COLUMN judgment_id UUID;
ALTER TABLE message_ledger_entry ADD COLUMN judgment_type VARCHAR(100);
ALTER TABLE message_ledger_entry ADD COLUMN verification_outcome VARCHAR(20);
ALTER TABLE message_ledger_entry ADD COLUMN evidence_quality DOUBLE PRECISION;

-- Range constraint on evidence_quality
ALTER TABLE message_ledger_entry ADD CONSTRAINT chk_evidence_quality
    CHECK (evidence_quality IS NULL OR (evidence_quality >= 0 AND evidence_quality <= 1));

-- Index for judgment EVENT queries (toolName discriminator + tenancy)
CREATE INDEX idx_mle_tenancy_toolname ON message_ledger_entry(tenancy_id, tool_name);
```

Null for non-judgment entries. Populated by `LedgerWriteService.record()` when the telemetry JSON contains `judgment_id`, `judgment_type`, `verification_outcome`, or `evidence_quality` keys.

---

## LedgerWriteService Changes

In `LedgerWriteService.record()`, after existing telemetry extraction:

```java
if (telemetryNode != null) {
    // existing extractions...
    
    // judgment fields
    entry.judgmentId = parseUuid(telemetryNode, "judgment_id");
    entry.judgmentType = telemetryNode.path("judgment_type").asText(null);
    entry.verificationOutcome = telemetryNode.path("verification_outcome").asText(null);
    double eq = telemetryNode.path("evidence_quality").asDouble(-1);
    entry.evidenceQuality = (eq >= 0 && eq <= 1) ? eq : null;
}
```

---

## MessageLedgerEntry Changes

Four new fields on `MessageLedgerEntry`:

```java
@Column(name = "judgment_id")
public UUID judgmentId;

@Column(name = "judgment_type", length = 100)
public String judgmentType;

@Column(name = "verification_outcome", length = 20)
public String verificationOutcome;

@Column(name = "evidence_quality")
public Double evidenceQuality;
```

`domainContentBytes()` updated with a tagged suffix for backward compatibility:

```java
@Override
protected byte[] domainContentBytes() {
    String canonical = String.join("|",
        // existing 14 fields unchanged...
        channelId     != null ? channelId.toString()     : "",
        // ... through ...
        contextWindowPct != null ? contextWindowPct.toString() : ""
    );
    // Judgment suffix: appended ONLY when any judgment field is set.
    // Non-judgment entries produce identical hashes to before — Merkle chain preserved.
    // Tagged separator "|J:" prevents collision with existing field values.
    if (judgmentId != null || judgmentType != null
            || verificationOutcome != null || evidenceQuality != null) {
        canonical += "|J:"
            + (judgmentId != null ? judgmentId.toString() : "") + "|"
            + (judgmentType != null ? judgmentType : "") + "|"
            + (verificationOutcome != null ? verificationOutcome : "") + "|"
            + (evidenceQuality != null ? String.valueOf(evidenceQuality) : "");
    }
    return canonical.getBytes(StandardCharsets.UTF_8);
}
```

**Backward compatibility:** Existing entries have all four judgment fields null → suffix not appended → hash unchanged → Merkle chain preserved. New judgment entries get the tagged suffix → collision-free encoding (pipe-delimited within the suffix, `|J:` tag distinguishes from existing field positions). V2003 routing columns are NOT in `domainContentBytes()` — this is a deliberate design choice for judgment fields because they are compliance-critical and must be tamper-evident.

---

## New Query Methods

Methods on `MessageLedgerEntryRepository`:

### findJudgmentEvents (for attribution report)

```java
public List<MessageLedgerEntry> findJudgmentEvents(
        UUID channelId, UUID judgmentId, Instant from, Instant to, String tenancyId) {
    // JPQL: FROM MessageLedgerEntry e
    //   WHERE e.tenancyId = :tenancyId
    //     AND e.toolName IN (:kinds)
    //     AND (:channelId IS NULL OR e.channelId = :channelId)
    //     AND (:judgmentId IS NULL OR e.judgmentId = :judgmentId)
    //     AND (:from IS NULL OR e.occurredAt >= :from)
    //     AND (:to IS NULL OR e.occurredAt <= :to)
    //   ORDER BY e.occurredAt ASC
    // :kinds = List.of(JudgmentEventKinds.YIELDED, .RESPONDED, .VERIFIED, .ESCALATED)
}
```

Uses `IN` with `JudgmentEventKinds` constants (not `LIKE`) for compile-time contract enforcement — the same constants used for writing are used for querying. Uses the `(tenancy_id, tool_name)` index.

### countJudgmentOutcomes (for fulfillment report — SQL aggregation)

```java
public List<Object[]> countJudgmentOutcomes(
        Instant from, Instant to, String tenancyId) {
    // JPQL: SELECT e.judgmentType, e.verificationOutcome, COUNT(e)
    //   FROM MessageLedgerEntry e
    //   WHERE e.tenancyId = :tenancyId
    //     AND e.toolName = :verifiedKind
    //     AND e.occurredAt >= :from AND e.occurredAt <= :to
    //   GROUP BY e.judgmentType, e.verificationOutcome
    // :verifiedKind = JudgmentEventKinds.VERIFIED
}
```

### findPendingJudgments (for fulfillment report — unbounded, all currently open)

```java
public List<MessageLedgerEntry> findPendingJudgments(String tenancyId) {
    // JPQL: FROM MessageLedgerEntry e
    //   WHERE e.tenancyId = :tenancyId
    //     AND e.toolName = :yieldedKind
    //     AND NOT EXISTS (SELECT 1 FROM MessageLedgerEntry v
    //       WHERE v.tenancyId = :tenancyId
    //         AND v.judgmentId = e.judgmentId
    //         AND v.toolName IN (:terminalKinds))
    // :yieldedKind = JudgmentEventKinds.YIELDED
    // :terminalKinds = List.of(JudgmentEventKinds.VERIFIED, JudgmentEventKinds.ESCALATED)
}
```

Not bounded by time range — follows the `ObligationReport.stillOpen` pattern.

---

## REST API

New endpoints in `ComplianceReportResource`:

```
GET /api/compliance/judgment-attribution/{judgmentId}
    Query params: limit (default 200)
    Returns: JudgmentAttributionReport

GET /api/compliance/judgment-fulfillment
    Query params: from (ISO-8601, required), to (ISO-8601, required),
                  judgmentType (optional), actorId (optional)
    Returns: JudgmentFulfillmentReport
```

Content negotiation via `Accept` header (JSON/CSV/HTML) — same as existing report endpoints.

---

## GraphQL API

New queries in `ComplianceQueryResolver`:

```graphql
type Query {
    complianceJudgmentAttribution(judgmentId: String!, limit: Int): JudgmentAttributionReportType
    complianceJudgmentFulfillment(from: String!, to: String!, judgmentType: String, actorId: String): JudgmentFulfillmentReportType
}
```

Annotated with `@McpDomain("qhorus")` for automatic MCP tool generation. New GraphQL DTOs: `JudgmentAttributionReportType`, `JudgmentFulfillmentReportType`, `JudgmentEventType`, `JudgmentTypeSummaryType`, `CallerSummaryType`.

---

## Scheduled Report Support

`JUDGMENT_FULFILLMENT` is schedulable via the existing `ComplianceReportScheduler`. `JUDGMENT_ATTRIBUTION` is NOT schedulable — it requires a specific `judgmentId`, which is an on-demand query, not a periodic sweep.

Changes required:
1. Add `case JUDGMENT_FULFILLMENT ->` branch to `ComplianceReportScheduler.generateAndStore()` switch (existing switch uses `default -> throw`). The scheduled generation uses `lastRunAt` → `now` as the `[from, to]` window, matching the existing `OBLIGATION` pattern.
2. Schedule creation validation: reject `reportType = JUDGMENT_ATTRIBUTION` at the service layer (same pattern as `channelId required for VIOLATION`).
3. `JUDGMENT_FULFILLMENT` does not require `channelId` (cross-channel by default, like `OBLIGATION`).

---

## Format Renderer Extensions

CSV flattening for new report types:
- **JudgmentAttribution:** one row per JudgmentEvent (columns: eventKind, actorId, occurredAt, evidenceQuality, verificationOutcome, escalationReason, trustScore, durationMs), followed by separator, then one row per causal node (same format as existing Attribution CSV)
- **JudgmentFulfillment:** two sections — by-type summary rows then by-caller summary rows (same sectioning pattern as existing Obligation CSV)

HTML rendering follows existing structured table pattern.

---

## Testing Strategy

| Component | Test type | Notes |
|-----------|----------|-------|
| `JudgmentAttributionReportService` | CDI-free unit tests | Mock `MessageLedgerEntryRepository`, `CausalGraphService`, `TrustGateService`. Verify event timeline construction, causal graph composition, Merkle root. |
| `JudgmentFulfillmentReportService` | CDI-free unit tests | Mock repository. Verify per-type and per-caller aggregation, acceptance rate computation, evidence quality averaging, pending detection. |
| `JudgmentEventKinds` | CDI-free unit tests | Verify constants match expected values, prefix consistency. |
| `MessageLedgerEntry.domainContentBytes()` | CDI-free unit tests | Verify judgment columns included in hash. Existing test extended. |
| `LedgerWriteService` telemetry extraction | CDI-free unit tests | Verify judgment fields extracted from telemetry JSON. |
| `findJudgmentEvents` repository method | `@QuarkusTest @TestTransaction` | Verify query filtering (channelId, judgmentId, time range, tenancyId). |
| REST endpoints | `@QuarkusTest` | HTTP round-trip, content negotiation for both report types. |
| GraphQL queries | `@QuarkusTest` | GraphQL query tests for both report types. |
| CSV/HTML renderers | CDI-free unit tests | Verify output structure for new report types. |
| V2004 migration | `FlywayMigrationSchemaTest` | Verify columns and index created correctly. |

Synthetic judgment EVENTs use `MessageLedgerEntryTestFactory` with judgment columns populated. Test data covers: single-step judgments (YIELDED→RESPONDED→VERIFIED), escalation chains (YIELDED→ESCALATED→RESPONDED→VERIFIED), multi-type aggregation, empty result sets.

---

## Implementation Notes

- `LedgerWriteService.record()` already handles EVENT telemetry extraction. The four new fields are additive — no change to existing extraction logic. Dual-storage fallback populates `sourceEntity` and `contextRefs` from judgment fields when those columns are null.
- All JPQL queries use `FROM MessageLedgerEntry` (not `LedgerEntry`) per the `ledger-entry-repository-cross-dtype-jpql` protocol — qhorus-scoped queries only.
- `JudgmentAttributionReportService` resolves `correlationId` from the YIELDED event's `correlationId` field. Judgment EVENTs MUST share the `correlationId` of the triggering COMMAND (explicit contract requirement in §Telemetry Contract). If `correlationId` is null on the YIELDED event, the causal graph section is empty (judgment events only, no message flow graph).
- `import-qhorus-test.sql` does not need updating — the new columns are on `message_ledger_entry` (JPA entity, not the `ledger_subject_sequence` table).
- The `(tenancy_id, tool_name)` index benefits all future tool-name-based queries, not just judgment. This is a general-purpose improvement.
- Fulfillment report uses SQL aggregation (`GROUP BY`, `COUNT`, `AVG`, `NOT EXISTS`) — does not load individual events into memory. The attribution report loads events for a single `judgmentId` (small result set).
- Multi-channel support: `JudgmentAttributionReport` uses `channelCount` + `List<String> channels` (same pattern as existing `AttributionReport`), not singular `channelId`. Judgment exchanges may span channels via escalation.
- V2003 routing columns are NOT in `domainContentBytes()` — they are not compliance-critical. Judgment columns ARE included because they are compliance evidence (Article 12 record-keeping). Different design choice for different data categories.

---

## References

- `AttributionReportService.java` (compliance-report/report/) — existing attribution report pattern
- `ObligationReportService.java` (compliance-report/report/) — existing obligation report pattern
- `MessageLedgerEntry.java` (runtime/ledger/) — entity, domainContentBytes()
- `LedgerWriteService.java` (runtime/ledger/) — telemetry extraction
- `MessageLedgerEntryRepository.java` (runtime/ledger/) — existing query methods
- `CausalGraphService.java` (runtime/ledger/) — causal graph infrastructure
- `V2003__message_ledger_entry_routing_metadata.sql` — routing column precedent
- `2026-08-27-compliance-evidence-export-design.md` — E5 parent spec
- decisions.md (D1-D4) — all captured design decisions
- Engine#998 issue body — "Schema designed for qhorus E5 compliance export"
- Parent epic #410 — governed yield governance vision
- R1-06, R1-08, R1-18, R1-20 — decision review findings incorporated
- Spec review findings incorporated: R1-02 (domainContentBytes backward compat), R1-03 (scheduled report), R1-04 (UUID type), R1-05 (SQL aggregation), R1-06 (pending scope), R1-07 (CallerSummary pending), R1-08 (correlationId contract), R1-09 (response time), R1-10 (evidence_quality CHECK), R1-12 (multi-channel), R1-13 (IN vs LIKE), R1-16 (dual storage)
