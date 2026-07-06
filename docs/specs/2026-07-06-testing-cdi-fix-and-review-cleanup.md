# Testing CDI Fix and Review Cleanup

**Issues:** #322, #325
**Date:** 2026-07-06
**Branch:** `issue-322-testing-persistence-memory-cdi-fix`

---

## 1. Testing Module Dependency Scope Fix (#322)

### Problem

`casehub-qhorus-testing/pom.xml` declares `casehub-qhorus-persistence-memory` as a **compile** dependency. The testing module exports only two classes — `MessageLedgerEntryTestFactory` and `RecordingChannelBackend` — neither of which imports or uses anything from `persistence-memory`. The only consumer is `CommitmentServiceTest` in `src/test/`, which instantiates `InMemoryCommitmentStore` directly for CDI-free unit tests.

The compile scope causes consumers that add `casehub-qhorus-testing` (e.g. for `RecordingChannelBackend`) to transitively inherit all `@Alternative @Priority(1)` InMemory stores from `persistence-memory`. These stores override JPA stores in the consumer's `@QuarkusTest` integration tests, causing either CDI ambiguity (when the consumer also has its own alternatives) or silent functional failures (InMemory stores active when JPA stores are expected).

Garden reference: GE-20260630-69e447.

### Fix

Change `persistence-memory` dependency scope from `compile` to `test` in `testing/pom.xml`. Fix `<description>` to reflect actual module content: "Test utilities for Qhorus consumers (RecordingChannelBackend, MessageLedgerEntryTestFactory)".

### Impact

- Consumers adding `casehub-qhorus-testing` no longer inherit InMemory stores transitively
- Consumers that need both test utilities AND InMemory stores explicitly add `persistence-memory` as a separate dependency
- `CommitmentServiceTest` continues to work — it only needs InMemory stores at test scope
- casehub-life's Maven exclusion workaround becomes unnecessary (tracked separately)

### CLAUDE.md update

Remove "depends on persistence-memory/ for transitive InMemory store access" from the testing module description.

---

## 2. OutboundMessage.correlationId: UUID → String (#325)

### Problem

`OutboundMessage.correlationId` is `UUID` but the domain model uses `String` everywhere (`Message.correlationId()`, `MessageDispatch.correlationId()`, `Commitment.correlationId()`). This type mismatch creates:

1. **Three identical `parseCorrelationUuid()` methods** — private static methods in `MessageService`, `ReactiveMessageService`, and `ChannelGateway`. Each safely returns null for non-UUID strings.
2. **One inline parse in `DeliveryBatchExecutor.toOutbound()`** — `UUID.fromString(m.correlationId())` with NO try-catch. This is a **latent bug**: a non-UUID correlationId throws `IllegalArgumentException`, crashing the delivery batch.
3. **Lossy conversion** — non-UUID correlationIds silently become null in `OutboundMessage`, meaning downstream consumers (A2A SSE matching, Slack thread lookup) lose correlation for those messages.

### Fix

Change `OutboundMessage.correlationId` from `UUID` to `String`.

```java
// Before
public record OutboundMessage(UUID messageId, String sender, MessageType type,
    String content, UUID correlationId, Long inReplyTo, ActorType senderActorType) {}

// After
public record OutboundMessage(UUID messageId, String sender, MessageType type,
    String content, String correlationId, Long inReplyTo, ActorType senderActorType) {}
```

### Cascade within qhorus

| File | Change |
|------|--------|
| `api/.../OutboundMessage.java` | `correlationId` field: `UUID` → `String`; update `@param correlationId` Javadoc (remove "UUID form" reference) |
| `runtime/.../MessageService.java` | Delete `parseCorrelationUuid()`, pass `dispatch.correlationId()` directly |
| `runtime/.../ReactiveMessageService.java` | Delete `parseCorrelationUuid()`, pass `dispatch.correlationId()` directly |
| `runtime/.../ChannelGateway.java` | Delete `parseCorrelationUuid()`, pass `msg.correlationId()` directly |
| `runtime/.../DeliveryBatchExecutor.java` | Remove `UUID.fromString()` call, pass `m.correlationId()` directly (fixes latent bug) |
| `runtime/.../A2AChannelBackend.java` | `sseStreams`: `ConcurrentHashMap<UUID, ...>` → `ConcurrentHashMap<String, ...>`; `registerStream/deregisterStream/streamCount` params: `UUID` → `String`; `receive()`: normalize UUID-format taskId to lowercase via `UUID.fromString(taskId).toString()` to preserve case-insensitive matching |
| `runtime/.../A2AResource.java` | Keep UUID validation for A2A protocol; register/deregister SSE stream with `corrId.toString()` (lowercase-normalized UUID) instead of raw `taskId` |
| `runtime/.../ReviewerChannelBackend.java` | Remove redundant `.toString()` calls on `message.correlationId()` (now returns String) |
| `runtime/.../DebateChannelBackend.java` | Remove redundant `.toString()` call on `message.correlationId()` (now returns String) |
| `slack-channel/.../SlackChannelBackend.java` | `threadCache` field: inner key `UUID` → `String`; `post()` corrId variable: `UUID` → `String`; `onInboundMessage()`: remove `.map(UUID::toString)` from `findCorrelationId()` result; `onChannelInitialised()` cache warm-up key follows `SlackThreadCacheId` change |
| `slack-channel/.../SlackThreadCacheStore.java` | `findThreadTs()`, `save()`, `delete()`: 2nd param `UUID` → `String`; `findCorrelationId()` return: `Optional<UUID>` → `Optional<String>`, JPQL SELECT type changes |
| `slack-channel/.../SlackThreadCacheId.java` | `correlationId` field: `UUID` → `String` |
| `slack-channel/.../SlackThreadCache.java` | `correlationId` column type change |
| Flyway **V27** | `ALTER TABLE slack_thread_cache ALTER COLUMN correlation_id TYPE VARCHAR(255) USING correlation_id::text` |
| Test files across `runtime/`, `connector-backend/`, `slack-channel/`, `api/` | Mechanical — construction sites pass String correlationId; compiler enforces completeness |

### Cross-repo impact

| Repo | Impact | Action |
|------|--------|--------|
| claudony | 2 test files construct `OutboundMessage` with UUID correlationId | File issue for mechanical migration |
| Other ChannelBackend consumers | `.correlationId()` return type changes from `UUID` to `String` — no construction-site changes | Compile-time break, mechanical fix |

---

## 3. Test Sleeps → Awaitility (#325)

### Problem

4 `Thread.sleep(100)` calls in `ChannelGatewayDeliverRemoteTest` waiting for virtual thread dispatch in `deliverRemote()`. Sleeps are unreliable — too short on slow CI, wasteful on fast machines.

### Fix

Replace with Awaitility polling assertions. The `posted` list is `CopyOnWriteArrayList` — already thread-safe for polling.

| Test | Assertion |
|------|-----------|
| `deliverRemote_callsPostOnBestEffortBackend` | `await().atMost(2, SECONDS).untilAsserted(() -> assertThat(posted).hasSize(1))` |
| `deliverRemote_skipsAgentBackend` | `await().during(200, MILLISECONDS).atMost(500, MILLISECONDS).untilAsserted(() -> assertThat(posted).isEmpty())` |
| `deliverRemote_skipsAtLeastOnceBackend` | Same pattern — sustained negative assertion |
| `deliverRemote_lazyInitializesUnknownChannel` | `await().atMost(2, SECONDS).untilAsserted(() -> assertThat(gateway.listBackends(channelId)).isNotEmpty())` |

### Dependency

`awaitility` is in the parent BOM. Add to `runtime/pom.xml` as test scope if not already present.

---

## 4. Exponential Backoff for PostgresChannelActivityBroadcaster (#325)

### Problem

Two reconnection paths in `acquireAndListen()`:
- **Path A** (line 154 — `pool.getConnection()` fails): fixed 5s delay
- **Path B** (line 149 — `closeHandler` fires): **zero delay** — tight loop on flapping connections

### Fix

Shared exponential backoff state for both paths.

| Parameter | Value |
|-----------|-------|
| Initial delay | 1 second |
| Multiplier | ×2 |
| Cap | 60 seconds |
| Reset trigger | Successful connection acquisition (line 139 success callback) |

Implementation: `AtomicLong currentDelayMs` field initialized to `INITIAL_DELAY_MS`. Both retry paths sleep `currentDelayMs.get()` then escalate via `currentDelayMs.updateAndGet(d -> Math.min(d * 2, MAX_DELAY_MS))`. On successful connection: `currentDelayMs.set(INITIAL_DELAY_MS)`.

Reconnection guard: `AtomicBoolean reconnecting` field prevents concurrent `acquireAndListen()` calls. Entry: `if (!reconnecting.compareAndSet(false, true)) return;`. Reset: `reconnecting.set(false)` after successful LISTEN, on LISTEN failure (then close the useless connection — its closeHandler triggers retry with backoff), and before retry after sleep in the getConnection error path. Previous connection cleanup: close `subscriberConnection` before assigning the new connection to prevent leaked LISTEN subscriptions.

The closeHandler path (currently zero delay) gets the same backoff — a flapping connection that succeeds then immediately closes escalates delay correctly.

---

## Implementation Order

1. **OutboundMessage type change** — touches the most files; do first so dependent changes compile
2. **Testing pom scope fix** — independent, small
3. **Awaitility test migration** — independent, small
4. **Broadcaster exponential backoff** — independent, small
5. **File GitHub issues** for out-of-scope cross-repo items (casehub-life exclusion, claudony migration, garden entry) before merge

## Out of Scope

- Removing casehub-life's Maven exclusion workaround for persistence-memory (cross-repo — file GitHub issue before merge)
- Claudony test file migration for OutboundMessage type change (cross-repo — file GitHub issue before merge)
- Garden entry GE-20260630-69e447 staleness update (file GitHub issue before merge)
- Reactive parity for broadcaster (no reactive broadcaster exists yet)
