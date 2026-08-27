# Design: Compliance Evidence Export — EU AI Act Audit Reports

**Issue:** casehubio/qhorus#402
**Date:** 2026-08-27
**Status:** Draft

---

## Context

EU AI Act enforcement date is August 2, 2026. Article 12 requires deployers of high-risk AI systems to maintain logs of system operation, including decision records, accountability chains, and governance evidence. Qhorus's tamper-evident ledger has the data; this work packages it into exportable compliance reports.

The platform already provides the raw query infrastructure:
- `CausalGraphService` — cross-channel causal graphs with delegation trees
- `MessageLedgerEntryRepository` — obligation stats, stalled commands, telemetry aggregation
- `TrustExportService` / `ActorTrustScoreRepository` — current trust score snapshots
- `LedgerEntryRepository` — time-range queries, attestation aggregates (from ledger#201)
- `EnforcementBlockedEvent` / `EnforcementExecutor` — enforcement violation data
- `LedgerProvExportService` / `LedgerProvSerializer` — per-subject PROV-JSON-LD export
- `LedgerComplianceReportService` — tamper-evident decision records with Merkle root anchors
- `LedgerVerificationService` — Merkle tree root and entry verification

### LedgerComplianceReportService Integration

`LedgerComplianceReportService` provides per-actor and per-subject compliance reports containing `ComplianceSupplement`-filtered entries (algorithmRef, confidenceScore, rationale, evidence, contestationUri, humanOverrideAvailable). Report type consumption:

| Report Type | Consumes LCRS? | Rationale |
|---|---|---|
| Attribution | Yes | Enriches causal graph nodes with ComplianceSupplement data — algorithmRef, confidenceScore, rationale for entries that represent automated decisions. Article 12 requires this per-decision context. |
| Obligation | No | Obligation fulfillment is about aggregate rates (fulfilled/failed/declined), not per-decision compliance supplements. The data is structurally different. |
| Trust History | No | Trust trajectories are derived from attestation scores, not compliance supplements. The trust score computation is a separate concern from the decision record. |
| Violation | No | Violation reports consume enforcement EVENTs from the ledger, not compliance supplements. Enforcement actions and compliance decision records are distinct. |
| Provenance | No | PROV-DM mapping operates on CausalGraphService's graph structure. LedgerProvExportService handles per-subject PROV export independently (D2). |

The gap is the **report composition and export** layer: aggregating across these data sources into coherent compliance reports with multiple export formats, REST/GraphQL/MCP exposure, and scheduled generation.

---

## Module Structure

New optional module `compliance-report/` activated by classpath presence.

SPI types live in the `api/` module (per `consumer-spi-placement` protocol):

```
api/src/main/java/io/casehub/qhorus/api/spi/compliance/
├── CompliancePostureProvider.java
├── CompliancePosture.java
├── PostureEntry.java
└── PostureStatus.java
```

Module layout:

```
compliance-report/
├── pom.xml
└── src/main/java/io/casehub/qhorus/compliance/
    ├── report/
    │   ├── AttributionReportService.java
    │   ├── ObligationReportService.java
    │   ├── TrustHistoryReportService.java
    │   ├── ViolationReportService.java
    │   └── ProvenanceReportService.java
    ├── model/
    │   ├── AttributionReport.java
    │   ├── ObligationReport.java
    │   ├── TrustHistoryReport.java
    │   ├── ViolationReport.java
    │   ├── ProvenanceReport.java
    │   ├── ReportType.java
    │   ├── ReportFormat.java
    │   └── ReportMetadata.java
    ├── format/
    │   ├── JsonReportRenderer.java
    │   ├── CsvReportRenderer.java
    │   └── HtmlReportRenderer.java
    ├── provdm/
    │   └── ProvJsonLdMapper.java
    ├── schedule/
    │   ├── ComplianceReportSchedule.java      (JPA entity)
    │   ├── ComplianceReportScheduleStore.java
    │   ├── ComplianceReportScheduler.java     (@Scheduled sweep)
    │   └── ComplianceReportGeneratedEvent.java (CDI event)
    ├── storage/
    │   ├── ComplianceReportRecord.java        (JPA entity — metadata index)
    │   └── ComplianceReportRecordStore.java
    ├── posture/
    │   └── NoOpCompliancePostureProvider.java (@DefaultBean — no external deps)
    ├── graphql/
    │   ├── ComplianceQueryResolver.java       (@GraphQLApi @McpDomain("qhorus"))
    │   ├── ComplianceMutationResolver.java    (@GraphQLApi @McpDomain("qhorus"))
    │   └── dto/
    │       ├── AttributionReportType.java
    │       ├── ObligationReportType.java
    │       ├── TrustHistoryReportType.java
    │       ├── ViolationReportType.java
    │       └── ProvenanceReportType.java
    └── api/
        ├── ComplianceReportResource.java      (REST)
        └── ComplianceReportResponse.java      (REST DTO)
```

GraphQL resolvers are in `compliance-report/` itself — not in the existing `graphql/` module. When `compliance-report/` is on the classpath, Jandex discovers the `@GraphQLApi` classes and the GraphQL schema is extended with compliance queries/mutations. When absent, no compliance queries exist in the schema. `@McpDomain("qhorus")` groups the compliance MCP tools with existing qhorus tools — multiple `@GraphQLApi` classes across modules already share the same `@McpDomain` value (QhorusQueryResolver, QhorusMutationResolver, QhorusSubscriptionResolver, QhorusModelEnricher).

### Dependencies

```xml
<!-- compile -->
<dependency>casehub-qhorus-api</dependency>
<dependency>casehub-qhorus</dependency>        <!-- runtime services -->
<dependency>casehub-ledger</dependency>         <!-- already in qhorus dep graph -->
<dependency>casehub-platform-api</dependency>   <!-- DigestSchedule, CurrentPrincipal -->
<dependency>casehub-platform-graphql</dependency> <!-- GraphQL resolver base -->

<!-- provided -->
<dependency>quarkus-smallrye-graphql</dependency>
<dependency>quarkus-hibernate-orm</dependency>
<dependency>jakarta.enterprise.cdi-api</dependency>
```

No dependency on `casehub-ops`. Compliance posture data accessed via `CompliancePostureProvider` SPI with `@DefaultBean NoOp`.

---

## Report Types

### 1. Attribution Chain Report

Full delegation tree for a given correlation ID. Maps to `CausalGraphService.buildGraph()` output enriched with:
- Per-node trust scores at the time of action (from trust score snapshots, D1)
- Attestation verdicts on terminal entries
- ComplianceSupplement data from `LedgerComplianceReportService` for entries representing automated decisions (algorithmRef, confidenceScore, rationale)
- Merkle root at generation time (D11)

```java
public record AttributionReport(
    String correlationId,
    String rootEntryId,
    int channelCount,
    List<String> channels,
    Long totalDurationMs,
    String outcome,
    List<AttributionNode> nodes,
    List<AttributionEdge> edges,
    String merkleRoot,
    Instant generatedAt,
    int schemaVersion
) {}

public record AttributionNode(
    String entryId, String channelId, String channelName,
    String messageType, String actorId, String occurredAt,
    String content, String causedByEntryId, int depth,
    Double trustScoreAtTime,
    String attestationVerdict,
    String algorithmRef,
    Double confidenceScore,
    String rationale
) {}

public record AttributionEdge(
    String from, String to, String type, Long elapsedMs
) {}
```

**Data sources:** `CausalGraphService.buildGraph()`, `TrustExportService.exportActor()` (or snapshot table when D1 lands), `LedgerEntryRepository.findAttestationsByEntryId()`, `LedgerVerificationService.treeRoot()`, `LedgerComplianceReportService.reportForSubject()` (for ComplianceSupplement enrichment on graph nodes)

**D1 fallback:** Until the ledger snapshot table lands, `trustScoreAtTime` uses `TrustGateService.currentScore()` — the current score, not the historical score at the node's timestamp. The field is nullable; consumers should check `schemaVersion` to determine whether historical scores are available.

**Merkle root (correlation-scoped):** Attribution reports span multiple ledger subjects (one per channel in the causal graph). The `merkleRoot` field uses the composite format from `LedgerComplianceReportService.buildActorMerkleRoot()`: semicolon-separated `subjectId=rootHash` pairs for all subjects referenced by graph nodes. Example: `"<channelId1>=abc123;<channelId2>=def456"`.

### 2. Obligation Fulfillment Report

Per-agent and per-channel obligation rates over a time range.

```java
public record ObligationReport(
    Instant from,
    Instant to,
    List<ChannelObligationSummary> channels,
    List<AgentObligationSummary> agents,
    int totalCommands,
    int fulfilled,
    int failed,
    int declined,
    int delegated,
    int stillOpen,
    int stalled,
    double overallFulfillmentRate,
    CompliancePosture posture,
    String merkleRoot,
    Instant generatedAt,
    int schemaVersion
) {}

public record ChannelObligationSummary(
    UUID channelId, String channelName,
    int total, int fulfilled, int failed, int declined,
    int delegated, int stillOpen, int stalled,
    double fulfillmentRate
) {}

public record AgentObligationSummary(
    String actorId,
    int total, int fulfilled, int failed, int declined,
    int delegated, int stillOpen, int stalled,
    double fulfillmentRate,
    Double currentTrustScore
) {}
```

**Data sources:** `MessageLedgerEntryRepository.countByOutcome()` (per channel — requires new time-filtered overload, see Implementation Notes), `CommitmentStore.findOpenByObligor()` (per agent), `MessageLedgerEntryRepository.findStalledCommands()`, `TrustGateService.currentScore()`, `CompliancePostureProvider.getPosture()` (SPI — returns `CompliancePosture.EMPTY` when casehub-ops absent)

**Temporal scope:** Metrics have different temporal scopes, documented in the report:
- `fulfilled`, `failed`, `declined`, `delegated` — time-bounded to `[from, to]` via the new `countByOutcome(channelId, from, to, tenancyId)` overload
- `stillOpen` — current state (all currently open commitments), not bounded to `[from, to]`. Open obligations persist regardless of when they were created; filtering by creation date would miss obligations opened before the reporting window that are still outstanding.
- `stalled` — obligations older than the `from` cutoff with no terminal sibling. Uses `findStalledCommands(channelId, from, tenancyId)` where `from` acts as the staleness threshold.

**Merkle root (cross-channel):** The `merkleRoot` field uses the composite format: semicolon-separated `subjectId=rootHash` pairs for all channels included in the report.

### 3. Trust Score History Report

Per-actor trust trajectories with attestation evidence over a time range.

```java
public record TrustHistoryReport(
    Instant from,
    Instant to,
    List<ActorTrustTrajectory> actors,
    String merkleRoot,
    Instant generatedAt,
    int schemaVersion
) {}

public record ActorTrustTrajectory(
    String actorId,
    Double currentScore,
    List<TrustSnapshot> trajectory,
    List<AttestationSummaryEntry> attestations
) {}

public record TrustSnapshot(
    Instant snapshotAt,
    double score,
    String scoreType,
    String capabilityTag
) {}

public record AttestationSummaryEntry(
    UUID entryId,
    String verdict,
    double confidence,
    String attestorId,
    Instant occurredAt
) {}
```

**Data sources:** Trust score snapshot table (D1 — ledger issue), `LedgerEntryRepository.findAttestationsByEntryId()`, `TrustExportService.exportActor()`, `LedgerEntryRepository.summariseAttestationsByActor()`

**Merkle root:** The `merkleRoot` field uses the composite format for all ledger subjects referenced by attestation entries in the report. Trust scores themselves are not Merkle-anchored (they live in `ActorTrustScore`, not the ledger); the root covers only the attestation entries that contribute to score computation.

**Note:** Until D1 lands in the ledger, this report falls back to `TrustExportService.exportActor()` for current scores only (no trajectory). The trajectory section is empty with a note: "Trust score history requires casehub-ledger snapshot support."

### 4. Protocol Violation Log

Enforcement actions, advisory violations, and blocked messages over a time range.

```java
public record ViolationReport(
    Instant from,
    Instant to,
    UUID channelId,
    String channelName,
    List<ViolationEntry> violations,
    int totalBlocked,
    int totalAdvisory,
    int totalQuarantined,
    Map<String, Integer> violationsBySource,
    String merkleRoot,
    Instant generatedAt,
    int schemaVersion
) {}

public record ViolationEntry(
    Instant occurredAt,
    String sender,
    String messageType,
    String enforcementMode,
    List<String> violationSources,
    List<String> violations,
    String action,
    UUID ledgerEntryId
) {}
```

**Data sources:** Enforcement EVENTs from `MessageLedgerEntryRepository` (filtered by sender `"system:enforcement"`), `EnforcementBlockedEvent` history (if persisted — currently CDI async event only, see Implementation Notes), watchdog alert EVENTs (sender `"system:watchdog"`)

**Implementation note:** `EnforcementBlockedEvent` is a CDI async event — it's not persisted. The violation log relies on enforcement EVENT messages dispatched by `EnforcementExecutor` to the channel (these ARE persisted as `MessageLedgerEntry`). The EVENT content carries the enforcement details as telemetry JSON.

**Merkle root:** Single-channel report — `merkleRoot` is `LedgerVerificationService.treeRoot(channelId, tenancyId)`.

### 5. PROV-DM Provenance Report

W3C PROV-JSON-LD export mapping `CausalGraphService` output to PROV vocabulary.

```java
public record ProvenanceReport(
    String correlationId,
    Map<String, Object> provJsonLd,
    Instant generatedAt,
    int schemaVersion
) {}
```

**PROV-DM mapping:**

| Qhorus concept | PROV-DM type | IRI | Notes |
|----------------|-------------|-----|-------|
| Agent (actorId) | `prov:Agent` | `ledger:actor/{actorId}` | Shared IRI with LedgerProvSerializer — same agent identity across per-subject and per-correlation PROV documents |
| COMMAND/QUERY | `prov:Activity` | `ledger:activity/{entryId}` | Start/end from timestamps |
| DONE/RESPONSE | `prov:Activity` (completion) | `ledger:activity/{entryId}` | `prov:wasInformedBy` linking to originating activity |
| SharedData artefact | `prov:Entity` | `ledger:entry/{entryId}` | `prov:wasGeneratedBy` the creating activity |
| HANDOFF (delegation) | `prov:actedOnBehalfOf` | — | Delegator → delegate relationship |
| Causal edge | `prov:wasDerivedFrom` | — | `causedByEntryId` edges |
| Channel | `prov:Location` | `qhorus:channel/{channelId}` | `prov:atLocation` on activities; qhorus-specific concept |

The `@context` includes four namespaces:
```json
{
  "prov": "http://www.w3.org/ns/prov#",
  "ledger": "https://casehubio.github.io/ledger#",
  "qhorus": "https://casehubio.github.io/qhorus#",
  "xsd": "http://www.w3.org/2001/XMLSchema#"
}
```

Shared entity IRIs (`ledger:actor/`, `ledger:entry/`, `ledger:activity/`) ensure that a PROV tool processing both per-subject (ledger) and per-correlation (qhorus) PROV documents treats matching entities as identical. The `qhorus:` namespace is used only for concepts not present in the ledger's PROV model (channels as locations).

**Data sources:** `CausalGraphService.buildGraph()`, `ProvJsonLdMapper` (new, in this module)

---

## Format Renderers

Three format renderers, selected by Accept header (REST) or explicit parameter (GraphQL/MCP):

| Format | Accept header | Renderer | Notes |
|--------|--------------|----------|-------|
| JSON | `application/json` (default) | `JsonReportRenderer` | Direct Jackson serialization of report records |
| CSV | `text/csv` | `CsvReportRenderer` | Flattened tabular output — one row per node/entry/agent |
| HTML | `text/html` | `HtmlReportRenderer` | Structured HTML with tables, headers, print-friendly CSS |

Renderers implement a common interface:

```java
public interface ReportRenderer {
    String contentType();
    byte[] render(Object report);
    boolean supports(ReportFormat format);
}
```

CSV flattening strategy per report type:
- **Attribution:** one row per node (columns: entryId, channel, messageType, actorId, timestamp, depth, trustScore, verdict, algorithmRef, confidenceScore)
- **Obligation:** two sections — channel summary rows then agent summary rows (header row between sections)
- **Trust history:** one row per snapshot point (columns: actorId, timestamp, score, scoreType, capabilityTag)
- **Violations:** one row per violation entry
- **PROV-DM:** not applicable — PROV-JSON-LD is JSON only

---

## REST API

All endpoints under `/api/compliance/`. Content negotiation via `Accept` header.

### On-demand report generation

```
GET /api/compliance/attribution/{correlationId}
    Query params: limit (default 200)
    Returns: AttributionReport

GET /api/compliance/obligations
    Query params: channel (optional, UUID or name), from (ISO-8601),
                  to (ISO-8601), actorId (optional)
    Returns: ObligationReport

GET /api/compliance/trust-history
    Query params: actorId (required), from (ISO-8601), to (ISO-8601)
    Returns: TrustHistoryReport

GET /api/compliance/violations
    Query params: channel (required, UUID or name), from (ISO-8601),
                  to (ISO-8601)
    Returns: ViolationReport

GET /api/compliance/provenance/{correlationId}
    Query params: limit (default 200)
    Returns: ProvenanceReport (PROV-JSON-LD only, ignores Accept header)
```

On-demand reports fire `ComplianceReportGeneratedEvent` with `scheduleId=null` for audit trail purposes (no storage — the report is returned to the caller).

### Stored report access

```
GET  /api/compliance/reports
    Query params: type (ReportType), from, to, limit (default 20)
    Returns: List<ComplianceReportRecord> (metadata only)

GET  /api/compliance/reports/{id}
    Accept: application/json (default), text/csv, text/html
    Returns: report body re-rendered from stored JSON to requested format

DELETE /api/compliance/reports/{id}
    Releases the SharedData artefact claim
```

**Retrieval flow:** Reports are always stored as JSON (the canonical, round-trippable representation). On retrieval, the REST resource:
1. Loads `ComplianceReportRecord` to determine `reportType`
2. Fetches the JSON body from SharedData via `artefactId`
3. Deserializes JSON to the appropriate report record type (using `reportType` discriminator)
4. Re-renders to the format requested by `Accept` header (default: JSON — no re-rendering needed)

This means any stored report can be retrieved in any supported format, not just the schedule's original format. The `format` field on `ComplianceReportRecord` records what format the schedule originally requested (audit trail) but does not constrain retrieval.

### Schedule CRUD

```
GET    /api/compliance/schedules
    Returns: List<ComplianceReportSchedule>

POST   /api/compliance/schedules
    Body: { reportType, channelId?, schedule: DigestSchedule, format }
    Validation: channelId required when reportType=VIOLATION
    Returns: ComplianceReportSchedule

PUT    /api/compliance/schedules/{id}
    Body: { schedule?, format?, enabled? }
    Returns: ComplianceReportSchedule

DELETE /api/compliance/schedules/{id}
```

---

## GraphQL API

GraphQL resolvers are in the `compliance-report/` module, discovered via Jandex when the module is on the classpath. Thin adapters calling service beans in the same module.

```graphql
type Query {
    complianceAttribution(correlationId: String!, limit: Int): AttributionReportType
    complianceObligations(channelId: ID, from: String!, to: String!, actorId: String): ObligationReportType
    complianceTrustHistory(actorId: String!, from: String!, to: String!): TrustHistoryReportType
    complianceViolations(channelId: ID!, from: String!, to: String!): ViolationReportType
    complianceProvenance(correlationId: String!, limit: Int): ProvenanceReportType
    complianceReports(type: String, from: String, to: String, limit: Int): [ComplianceReportRecordType]
    complianceSchedules: [ComplianceReportScheduleType]
}

type Mutation {
    createComplianceSchedule(input: ComplianceScheduleInput!): ComplianceReportScheduleType
    updateComplianceSchedule(id: ID!, input: ComplianceScheduleUpdateInput!): ComplianceReportScheduleType
    deleteComplianceSchedule(id: ID!): Boolean
}
```

GraphQL resolvers annotated with `@McpDomain("qhorus")` for automatic MCP tool generation. Multiple `@GraphQLApi` classes share the same `@McpDomain` value — the platform's MCP tool generator groups by domain, not by module.

---

## Scheduled Report Generation

### Schedule entity

```java
@Entity
@Table(name = "compliance_report_schedule")
public class ComplianceReportSchedule {
    @Id @GeneratedValue UUID id;
    @Enumerated(STRING) ReportType reportType;
    UUID channelId;              // nullable — null = cross-channel (not valid for VIOLATION)
    @Column(columnDefinition = "TEXT")
    String scheduleJson;         // serialized DigestSchedule
    @Enumerated(STRING) ReportFormat format;
    String tenancyId;
    boolean enabled;
    Instant lastRunAt;
    Instant createdAt;
}
```

**Schedule validation:** `channelId` must be non-null when `reportType=VIOLATION`. ViolationReport is inherently channel-scoped — cross-channel violations have no meaningful aggregation point. Enforced by service-layer validation at schedule creation and update.

### Sweep mechanism

```java
@ApplicationScoped
public class ComplianceReportScheduler {

    @Scheduled(every = "1h")
    void sweep() {
        Instant now = Instant.now();
        for (ComplianceReportSchedule schedule : store.findEnabled()) {
            try {
                DigestSchedule timing = deserialize(schedule.scheduleJson);
                Instant lastRun = schedule.lastRunAt != null ? schedule.lastRunAt : Instant.EPOCH;
                if (timing.isFlushDue(lastRun, lastRun, now)) {
                    generateAndStore(schedule, now);
                    store.updateLastRunAt(schedule.id, now);
                }
            } catch (Exception e) {
                log.warn("Compliance report generation failed for schedule {}: {}",
                         schedule.id, e.getMessage(), e);
            }
        }
    }
}
```

Per-schedule error isolation ensures one failing schedule does not block remaining schedules. If `generateAndStore` throws (channel deleted, data source unavailable, serialization error), the exception is caught, logged, and the sweep continues with the next schedule. Since `updateLastRunAt` only runs after success, a failing schedule will retry on the next sweep — this is correct for transient failures. For persistent failures (e.g., deleted channel), the schedule retries hourly with a logged warning each time; operators should disable or delete the broken schedule.

Uses `CrossTenantComplianceReportScheduleStore` (per `scheduled-service-cross-tenant-stores` protocol) since the scheduler runs outside request context.

### Report storage

Generated reports stored via two writes in the same transaction:
1. Report body → `DataService.store()` → SharedData artefact (UUID)
2. Metadata → `ComplianceReportRecord` entity (references artefact UUID)

**`DataService.store()` parameter conventions:**

| Parameter | Value | Notes |
|---|---|---|
| `key` | `"compliance-report/{reportType}/{tenancyId}/{generatedAt-ISO}"` | Unique per report; reportType is enum name, generatedAt is millisecond-precision ISO-8601 |
| `description` | `"Compliance {reportType} report for tenant {tenancyId}"` | Human-readable |
| `createdBy` | `"system:compliance-scheduler"` | Scheduler runs outside request context (no CurrentPrincipal) |
| `content` | Rendered report body (JSON) | Always stored as JSON — the canonical, round-trippable representation. Re-rendered to the requested format on retrieval via `Accept` header content negotiation. |
| `append` | `false` | Each report is a single-chunk, complete write |
| `lastChunk` | `true` | Marks SharedData as complete |

`DataService.store()` returns `SharedData`. The `artefact_id` FK in `compliance_report` references `SharedData.id()`.

```java
@Entity
@Table(name = "compliance_report")
public class ComplianceReportRecord {
    @Id @GeneratedValue UUID id;
    @Enumerated(STRING) ReportType reportType;
    String tenancyId;
    Instant generatedAt;
    UUID scheduleId;             // nullable — null = on-demand
    UUID artefactId;             // FK to SharedData
    int schemaVersion;
    @Enumerated(STRING) ReportFormat format;
}
```

### Notification

After generating a report (scheduled or on-demand), fire CDI event:

```java
public record ComplianceReportGeneratedEvent(
    UUID reportId,
    ReportType reportType,
    String tenancyId,
    UUID artefactId,
    Instant generatedAt,
    UUID scheduleId,             // nullable — null = on-demand generation
    String requestedBy,          // CurrentPrincipal.actorId() for on-demand, "system:compliance-scheduler" for scheduled
    Map<String, String> requestParameters  // request-specific params; empty for scheduled (params are on the schedule entity)
) {}
```

**Field semantics by generation mode:**

| Field | Scheduled | On-demand |
|---|---|---|
| `reportId` | UUID of stored `ComplianceReportRecord` | `null` (not stored) |
| `artefactId` | UUID of stored `SharedData` | `null` (not stored) |
| `scheduleId` | UUID of triggering schedule | `null` |
| `requestedBy` | `"system:compliance-scheduler"` | `CurrentPrincipal.actorId()` |
| `requestParameters` | empty map (parameters are on the schedule entity) | populated from request (e.g., `correlationId`, `channelId`, `from`, `to`, `actorId`, `limit`) |

This provides a meaningful audit trail: "actor X generated an ATTRIBUTION report for correlationId=abc123 at time T" rather than just "someone generated an ATTRIBUTION report at time T." Observers can log, forward, or alert based on the full request context.

Observers (notification-bridge, webhook-observer, etc.) handle delivery through their own channels.

---

## CompliancePostureProvider SPI

SPI interface and model types in `casehub-qhorus-api` (per `consumer-spi-placement` protocol — `casehub-ops` implements the interface, so it must not depend on the full `compliance-report/` module):

**Location:** `api/src/main/java/io/casehub/qhorus/api/spi/compliance/`

```java
public interface CompliancePostureProvider {
    CompliancePosture getPosture(String tenancyId, Instant from, Instant to);
}

public record CompliancePosture(
    List<PostureEntry> entries
) {
    public static final CompliancePosture EMPTY = new CompliancePosture(List.of());
}

public record PostureEntry(
    String category,
    PostureStatus status,
    String description,
    String evidence,
    Instant checkedAt
) {}

public enum PostureStatus {
    COMPLIANT, NON_COMPLIANT, PARTIAL, UNKNOWN
}
```

`@DefaultBean` in `compliance-report/posture/` (no external deps — trivially returns `EMPTY`):

```java
@DefaultBean
@ApplicationScoped
public class NoOpCompliancePostureProvider implements CompliancePostureProvider {
    @Override
    public CompliancePosture getPosture(String tenancyId, Instant from, Instant to) {
        return CompliancePosture.EMPTY;
    }
}
```

`casehub-ops` depends on `casehub-qhorus-api` only (lightweight — pure Java, no JPA, no Quarkus runtime deps) and provides `@Alternative @Priority(1)` when on classpath. This follows the established pattern: `ObligorTrustPolicy` and `CommitmentAttestationPolicy` are both in `api/spi/` with default implementations in `runtime/`.

---

## Database Migrations

Migrations are in `db/qhorus/migration/` within the **runtime module** (`runtime/src/main/resources/db/qhorus/migration/`), following the established qhorus pattern. All existing qhorus migrations (V1–V46) are in this path, within the qhorus sequential version range. The compliance tables are created in the qhorus named datasource regardless of whether `compliance-report/` is deployed — the schema is stable across deployments; empty tables when the module is absent are harmless.

Native image resource registration is handled by the existing `QhorusProcessor.registerMigrationResources()` in the deployment module, which includes `db/qhorus/migration/*.sql`.

**V47:** `compliance_report_schedule` table
```sql
CREATE TABLE compliance_report_schedule (
    id UUID PRIMARY KEY,
    report_type VARCHAR(50) NOT NULL,
    channel_id UUID,
    schedule_json TEXT NOT NULL,
    format VARCHAR(10) NOT NULL,
    tenancy_id VARCHAR(255) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT true,
    last_run_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
```

**V48:** `compliance_report` metadata index
```sql
CREATE TABLE compliance_report (
    id UUID PRIMARY KEY,
    report_type VARCHAR(50) NOT NULL,
    tenancy_id VARCHAR(255) NOT NULL,
    generated_at TIMESTAMP NOT NULL,
    schedule_id UUID REFERENCES compliance_report_schedule(id),
    artefact_id UUID NOT NULL,
    schema_version INT NOT NULL DEFAULT 1,
    format VARCHAR(10) NOT NULL
);
CREATE INDEX idx_compliance_report_type_tenant ON compliance_report(report_type, tenancy_id);
CREATE INDEX idx_compliance_report_generated ON compliance_report(generated_at);
```

---

## Report Integrity

Each generated report includes a `merkleRoot` field — the current Merkle tree root hash(es) for referenced subjects, obtained from `LedgerVerificationService.treeRoot()`. This anchors the report to the ledger state at generation time. If any ledger entry referenced in the report is later modified, the Merkle root won't match.

**Composite Merkle root for multi-subject reports:** Reports that span multiple ledger subjects (AttributionReport across channels, ObligationReport cross-channel, TrustHistoryReport across attestation subjects) use a semicolon-separated `subjectId=rootHash` format, consistent with the established `LedgerComplianceReportService.buildActorMerkleRoot()` pattern. Example: `"<uuid1>=abc123;<uuid2>=def456"`. Single-subject reports (ViolationReport — per-channel) use a plain root hash string.

Digital signatures (eIDAS qualified seals, PKI) for external regulatory submission are deferred alongside PDF rendering.

**Note:** Merkle roots are per-subject and change as new entries are appended. The stored root proves no entries were MODIFIED since report generation — new entries are expected and do not invalidate the root.

---

## Report Versioning

Reports are immutable snapshots. Each carries `schemaVersion` (int), versioned **per report type**. Attribution schema version 2 does not affect Violation schema version 1 — each type evolves independently. The `compliance_report` metadata table's `schema_version` is meaningful only in conjunction with `report_type`.

When a report type's format changes:
1. Increment that type's schema version
2. New reports of that type use the new version
3. Old reports are never re-generated — they represent compliance state at generation time
4. Consumers filter by `(report_type, schema_version)` via the metadata table

---

## Tenant Isolation

- Schedules are per-tenant (`tenancy_id` required)
- Reports are per-tenant (inherited from schedule or from `CurrentPrincipal`)
- Sweep uses `CrossTenantComplianceReportScheduleStore` with explicit tenancy
- REST endpoints read tenancy from `CurrentPrincipal` (via `InboundTenancyContext`)
- One tenant's schedule cadence does not affect another's

---

## Retention Policy

Compliance reports accumulate in `compliance_report` + SharedData. Retention has bidirectional regulatory constraints:
- **Minimum retention:** EU AI Act Art.12(2) requires deployers to keep records for the duration of the AI system's lifecycle plus a reasonable period. Typical guidance: 5–10 years.
- **Maximum retention:** GDPR data minimization (Art.5(1)(e)) requires deletion when no longer necessary for the purpose.

**Current mechanism:** Manual cleanup via `DELETE /api/compliance/reports/{id}`, which releases the SharedData artefact claim. No automatic retention or purge.

**Automated retention (deferred):** A `@Scheduled` purge sweep with configurable `retention-days` per tenant (per `store-owned-retention-mechanism` protocol — JPA stores use `@Scheduled` DELETE, idiomatic for RDBMS). The retention period is tenant-specific because different deployers operate under different regulatory environments. This requires legal guidance on minimum retention periods before implementation.

---

## Ledger Dependency: Trust Score Snapshot Table

This design depends on a new `actor_trust_score_snapshot` table in casehub-ledger (D1). A separate ledger issue will be filed for:
- Snapshot table schema (actorId, scoreType, capabilityTag, dimension, score, snapshotAt)
- Snapshot writer triggered after `TrustScoreCalculator` runs
- Query methods for time-range trajectory retrieval

Until D1 lands, the trust history report degrades gracefully — returns current scores only (via `TrustExportService.exportActor()`) with empty trajectory.

---

## Testing Strategy

| Component | Test type | Notes |
|-----------|----------|-------|
| Report services | CDI-free unit tests | Mock stores and services, verify aggregation logic |
| Format renderers | CDI-free unit tests | Verify JSON/CSV/HTML output structure; adversarial content testing (embedded commas, quotes, newlines, Unicode, null/empty fields) for CSV RFC 4180 compliance |
| ProvJsonLdMapper | CDI-free unit tests | Verify PROV vocabulary mapping from CausalGraph; verify shared IRIs match LedgerProvSerializer output format |
| REST resources | `@QuarkusTest` | Full HTTP round-trip, content negotiation |
| GraphQL resolvers | `@QuarkusTest` | GraphQL query/mutation tests |
| Scheduler | CDI-free unit tests | Mock `DigestSchedule.isFlushDue()`, verify sweep logic |
| Schedule persistence | `@QuarkusTest @TestTransaction` | CRUD operations on schedule and report tables |
| Schedule validation | CDI-free unit tests | channelId required for VIOLATION type, format validation |
| Tenant isolation | `@QuarkusTest` | Verify cross-tenant data boundaries |
| SPI displacement | `@QuarkusTest @TestProfile` | Verify `CompliancePostureProvider` `@DefaultBean` is displaced |
| Composite merkle root | CDI-free unit tests | Verify semicolon-separated `subjectId=rootHash` format |

Test module uses `persistence-memory` stores where possible. Report generation integration tests use `QuarkusTransaction.requiringNew()` for dispatch (per observer-test-transaction-discipline protocol).

---

## Implementation Notes

- **New `countByOutcome` overload required in `MessageLedgerEntryRepository`:** The existing `countByOutcome(UUID channelId, String tenancyId)` counts all-time outcomes. A new `countByOutcome(UUID channelId, Instant from, Instant to, String tenancyId)` overload is needed, adding `AND e.occurredAt >= :from AND e.occurredAt <= :to` to the JPQL query. This is a qhorus runtime change (not just a compliance-report addition) and should be included in the implementation scope.
- Enforcement EVENTs (sender `"system:enforcement"`) are the persisted record of enforcement actions. The `EnforcementBlockedEvent` CDI event is transient. The violation report queries ledger entries, not CDI event history.
- Watchdog containment EVENTs (sender `"system:watchdog"`) are also persisted as ledger entries. The violation report includes these.
- `ChannelStore.findByIds()` used for batch channel name resolution in cross-channel reports (same pattern as `CausalGraphService`).
- `QhorusEntityMapper` shared for consistent DTO mapping.
- CSV rendering does not use external libraries — `StringBuilder` with proper escaping (RFC 4180).
- HTML rendering uses `StringBuilder` with inline CSS — no template engine dependency.

---

## Deferred Work

| Item | Reason | Issue |
|------|--------|-------|
| PDF rendering | Heavyweight dependency (OpenHTMLtoPDF), requires HTML templates, digital signatures | casehubio/qhorus#417 |
| Digital signatures (eIDAS) | Requires PKI infrastructure, regulatory review | casehubio/qhorus#418 |
| Trust score snapshot table | Ledger schema change | casehubio/ledger#203 |
| Automated retention policy | Requires legal guidance on retention periods | casehubio/qhorus#419 |
| casehub-ops `CompliancePostureProvider` impl | ops is research-stage | Filed when ops stabilizes |

---

## References

- `CausalGraphService.java` (runtime/ledger/) — attribution chain data source
- `MessageLedgerEntryRepository.java` (runtime/ledger/) — obligation and violation queries
- `TrustExportService.java` (casehub-ledger) — trust score export
- `LedgerProvSerializer.java` (casehub-ledger) — existing PROV-JSON-LD format
- `LedgerComplianceReportService.java` (casehub-ledger) — tamper-evident decision records
- `LedgerVerificationService.java` (casehub-ledger) — Merkle root anchors
- `DigestSchedule.java` (casehub-platform-api) — scheduling semantics
- `NotificationDeliverer.java` (casehub-platform-api) — delivery pipeline
- `EnforcementExecutor.java` (runtime/message/) — enforcement EVENT generation
- `QhorusQueryResolver.java` (graphql/) — existing @McpDomain pattern
- `CausalGraphResource.java` (runtime/api/) — existing REST resource pattern
- W3C PROV-JSON spec — https://www.w3.org/Submission/prov-json/
- W3C PROV-DM — https://www.w3.org/TR/prov-dm/
- EU AI Act Article 12 — record-keeping requirements
- decisions.md (D1-D12) — all captured design decisions
