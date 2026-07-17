# Correlation Integrity, QUERY Tracking, and Context Telemetry

**Date:** 2026-07-17
**Issues:** #353, #362, #363
**Branch:** issue-353-correlation-query-telemetry

## Problem

The commitment model is type-blind at resolution time. Both QUERY and COMMAND create
identical Commitment records, but the infrastructure does not enforce which terminal
message types are valid for which obligation types, who is authorised to resolve an
obligation, or whether message references are structurally valid. Separately, context
window pressure — the leading cause of enterprise AI degradation (65%) — is invisible
to the infrastructure.

### Root finding

Issue #362's premise that "QUERYs have no tracking" is incorrect. `MessageService.dispatch()`
line 305 already creates Commitment records for QUERYs with a correlationId:

```java
case QUERY, COMMAND -> commitmentService.open(
    storedCommitmentId, dispatch.correlationId(), dispatch.channelId(),
    dispatch.type(), dispatch.sender(), dispatch.target(), saved.deadline());
```

The real gap is type-aware resolution and a sensible default deadline for QUERYs.

## Design

### 1. CorrelationIntegrityChecker (#353)

New `@ApplicationScoped` bean in `io.casehub.qhorus.runtime.message`. Follows the
existing pattern where every enforcement check is a delegated bean (AllowedWritersPolicy,
RateLimiter, MessageTypePolicy, ObligorTrustPolicy).

**Injections:** `CommitmentStore`, `MessageStore`

**API:**

```java
public List<String> check(MessageDispatch dispatch, UUID channelId)
```

Called in `MessageService.dispatch()` after message type policy checks, before message
persistence. Returned advisories merge into `DispatchResult.advisories()`.

**Four advisory checks:**

**1a. inReplyTo validation** (independent of correlationId):
- `dispatch.inReplyTo()` non-null, message not found in store -> advisory
- `dispatch.inReplyTo()` found but `parent.channelId() != channelId` -> advisory

**1b. Resolution type matching** (requires: correlationId + terminal type + commitment found):
- RESPONSE resolving a COMMAND obligation -> advisory ("expected DONE/FAILURE/DECLINE")
- DONE or FAILURE resolving a QUERY obligation -> advisory ("expected RESPONSE/DECLINE")
- DECLINE is valid for both COMMAND and QUERY obligations
- Ref: PP-20260623-fd69f3

**1c. Obligor identity** (requires: correlationId + terminal type + commitment with non-null obligor):
- Sender is neither `commitment.obligor()` nor `commitment.delegatedTo()` -> advisory
- Applies to DONE, FAILURE, DECLINE, RESPONSE, and HANDOFF
- Null obligor (broadcast QUERY with no named target) -> check skipped

**1d. Cross-channel resolution** (requires: correlationId + terminal type + commitment found):
- `commitment.channelId() != dispatch.channelId()` -> advisory

**Edge cases:**
- No commitment found for correlationId -> no advisory (may be expired or cleaned up)
- Commitment already in terminal state -> no advisory (duplicate resolution is separate)
- All checks are advisory (WARN + DispatchResult.advisories()), not blocking

### 2. Default QUERY Deadline (#362)

**Config:** `casehub.qhorus.commitment.default-query-deadline` — `Optional<Duration>`,
absent by default. Added to `QhorusConfig.Commitment`.

**Placement:** In `MessageService.dispatch()`, inside the commitment switch block for
QUERY. When the sender did not set an explicit deadline and the config is present,
compute `Instant.now().plus(defaultQueryDeadline)` and pass to `commitmentService.open()`.

**Key decision:** The default deadline applies only to the Commitment's `expiresAt`,
not to `Message.deadline`. `Message.deadline` represents the sender's explicit intent;
the Commitment carries the infrastructure-provided default. Downstream tools can
distinguish "sender set 5 minutes" from "infrastructure applied 10 minutes."

**What this enables with no new code:**
- `CommitmentService.expireOverdue()` catches stale QUERYs automatically
- `CommitmentExpiredEvent` fires -> notification bridge (#365) notifies the requester
- No new watchdog condition needed — the existing scheduler handles it

### 3. Context Window Telemetry (#363)

**MessageLedgerEntry** — new nullable field:

```java
@Column(name = "context_window_pct")
public Integer contextWindowPct;
```

**V2002 migration** (`db/qhorus/migration/V2002__message_ledger_entry_context_window_pct.sql`):

```sql
ALTER TABLE message_ledger_entry ADD COLUMN context_window_pct SMALLINT;
```

**LedgerWriteService.populateTelemetry()** — parse `context_window_pct` from EVENT
telemetry JSON alongside existing `tool_name`, `duration_ms`, `token_count`, `context_refs`,
`source_entity`.

**Watchdog condition: `CONTEXT_PRESSURE`** — new method in `WatchdogEvaluationService`.
Queries latest EVENT entries per agent (by subjectId) in the target channel where
`contextWindowPct` is non-null. Fires if any agent's latest value >= `thresholdCount`
(reusing the existing Watchdog field semantics).

Requires a new query in `MessageLedgerEntryRepository`:
`findLatestContextPressureByChannel(UUID channelId, String tenancyId)` returning the
latest `contextWindowPct` per subjectId.

**MCP tool:** `get_telemetry_summary` gains context pressure stats — latest
`contextWindowPct` per agent.

### 4. Reactive Parity

The blocking path is the active production path (`reactive.enabled` defaults to `false`).
`CorrelationIntegrityChecker` uses blocking stores. Reactive parity (a
`ReactiveCorrelationIntegrityChecker`) is a follow-up issue — all reactive
`@QuarkusTest` tests are `@Disabled` (require PostgreSQL DevServices).

### 5. Integration in MessageService.dispatch()

Current enforcement gate order (for context):

1. Tenancy check
2. Paused check
3. AllowedWritersPolicy ACL (non-EVENT)
4. RateLimiter (non-EVENT)
5. ObligorTrustPolicy (COMMAND + named target)
6. MessageTypePolicy validate + advisory
7. **NEW: CorrelationIntegrityChecker** (advisory only)
8. **NEW: Default QUERY deadline** (applied at commitment open)
9. Message persistence
10. Commitment lifecycle
11. Ledger write (+ **NEW: contextWindowPct** parsing)
12. Observer dispatch
13. Fan-out + delivery signal

## Files Changed

| File | Change |
|------|--------|
| `runtime/.../message/CorrelationIntegrityChecker.java` | New — four advisory checks |
| `runtime/.../message/MessageService.java` | Inject checker, call after type policy, merge advisories; apply default QUERY deadline in commitment switch |
| `runtime/.../config/QhorusConfig.java` | Add `default-query-deadline` under Commitment |
| `runtime/.../ledger/MessageLedgerEntry.java` | Add `contextWindowPct` field |
| `runtime/.../ledger/LedgerWriteService.java` | Parse `context_window_pct` in `populateTelemetry()` |
| `runtime/.../ledger/MessageLedgerEntryRepository.java` | New query: latest context pressure per agent |
| `runtime/.../watchdog/WatchdogEvaluationService.java` | Add `CONTEXT_PRESSURE` condition |
| `runtime/.../mcp/QhorusMcpToolsBase.java` | Context pressure in telemetry summary |
| `db/qhorus/migration/V2002__message_ledger_entry_context_window_pct.sql` | New column |

## Testing Strategy

**CorrelationIntegrityChecker** — CDI-free unit tests with `InMemoryCommitmentStore` +
`InMemoryMessageStore`. Each check tested independently: inReplyTo not found, wrong
channel, obligor mismatch, type mismatch, cross-channel resolution, null obligor
(skipped), no commitment (no advisory). Integration test via `messageService.dispatch()`
confirming advisories propagate to `DispatchResult`.

**Default QUERY deadline** — unit test: config present -> deadline applied to commitment,
message deadline stays null. Config absent -> no change. Integration: QUERY with default
deadline gets expired by `expireOverdue()`.

**Context telemetry** — unit test for `populateTelemetry()` parsing `context_window_pct`.
`FlywayMigrationSchemaTest` for V2002. Watchdog unit test for `CONTEXT_PRESSURE`
condition evaluation.

## Out of Scope

- Reactive `CorrelationIntegrityChecker` (follow-up issue)
- Persisting advisories in ledger entries (advisories are logged at WARN and returned
  in DispatchResult; the attestation layer independently detects violations post-facto)
- New ACKNOWLEDGE message type (STATUS already serves this role; CommitmentState.ACKNOWLEDGED
  already exists)
