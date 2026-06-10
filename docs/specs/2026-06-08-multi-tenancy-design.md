# Multi-Tenancy Design — casehub-qhorus #260

**Date:** 2026-06-08
**Issue:** [casehubio/qhorus#260](https://github.com/casehubio/qhorus/issues/260)
**Status:** Approved

---

## Problem

All Qhorus Channels, Messages, and Commitments are currently shared across tenants. Every agent in every tenant can see every channel and every message. The platform multi-tenancy foundation is complete (`casehub-platform-api` ships `CurrentPrincipal.tenancyId()` and `TenancyConstants`); qhorus is the last foundation repo without tenant scoping.

---

## Governing Protocols

| Protocol | Rule |
|---|---|
| PP-20260520-439daf | Tenancy filtering always unconditional — never gated on deployment mode |
| PP-20260520-e6a5f0 | tenancyId binding inside data access classes; declared exception: enforcement gates (MessageService, ChannelService) that own entity stamping |
| PP-20260607-69eba2 | tenancyId is server-side infrastructure — never in REST DTOs, MCP tool params, or SSE events |
| PP-20260523-a08b97 | MessageService.dispatch() is the single enforcement gate — no caller may bypass it |
| GE-20260531-446fea | Thread tenancyId through entity data at registration time for no-CDI-context callers (scheduler) |
| GE-20260528-55a526 | Capture CurrentPrincipal before entering Uni pipeline in reactive stores |
| GE-20260601-a35fb3 | InMemory stores must not use tenancyId equality when stored value may be null |

---

## Schema — Flyway Migrations

**V18 — channel tenancy:**
```sql
ALTER TABLE channel
  ADD COLUMN tenancy_id VARCHAR(255) NOT NULL DEFAULT '278776f9-e1b0-46fb-9032-8bddebdcf9ce';
ALTER TABLE channel DROP CONSTRAINT uq_channel_name;
ALTER TABLE channel ADD CONSTRAINT uq_channel_name_tenancy UNIQUE (tenancy_id, name);
```

`uq_channel_name` becomes `uq_channel_name_tenancy(tenancy_id, name)` — two tenants may share a channel name.

**V19 — message tenancy:**
```sql
ALTER TABLE qhorus_message
  ADD COLUMN tenancy_id VARCHAR(255) NOT NULL DEFAULT '278776f9-e1b0-46fb-9032-8bddebdcf9ce';
```

**V20 — commitment tenancy:**
```sql
ALTER TABLE commitment
  ADD COLUMN tenancy_id VARCHAR(255) NOT NULL DEFAULT '278776f9-e1b0-46fb-9032-8bddebdcf9ce';
```

**V21 — watchdog tenancy:**
```sql
ALTER TABLE watchdog
  ADD COLUMN tenancy_id VARCHAR(255) NOT NULL DEFAULT '278776f9-e1b0-46fb-9032-8bddebdcf9ce';
```

The DEFAULT sentinel is `TenancyConstants.DEFAULT_TENANT_ID` — correct for all existing single-tenant rows.

---

## Qualifiers and System Principal

### `@CrossTenant` qualifier
Location: `api/src/main/java/io/casehub/qhorus/api/qualifier/CrossTenant.java`

CDI qualifier marking injection points that require cross-tenant data access. Lives in `api/` because cross-tenant store interfaces are also in `api/spi/` — consumer repos that implement those interfaces need the qualifier without depending on `runtime/`.

### `@QhorusSystem` qualifier
Location: `runtime/.../qualifier/QhorusSystem.java`

Internal qualifier selecting `QhorusSystemCurrentPrincipal` specifically, without displacing the `@DefaultBean` mock. Not part of the public SPI surface.

### `QhorusSystemCurrentPrincipal`
Location: `runtime/.../identity/QhorusSystemCurrentPrincipal.java`

```java
@ApplicationScoped @QhorusSystem
public class QhorusSystemCurrentPrincipal implements CurrentPrincipal {
    public String actorId()           { return "system:qhorus"; }
    public Set<String> groups()       { return Set.of(); }
    public String tenancyId()         { return TenancyConstants.DEFAULT_TENANT_ID; }
    public boolean isCrossTenantAdmin() { return true; }
}
```

Interim — replace when `casehub-platform` ships a platform-level system-actor principal with `isCrossTenantAdmin()=true`.

### `CrossTenantProducer`
Location: `runtime/.../identity/CrossTenantProducer.java`

Injects `@QhorusSystem QhorusSystemCurrentPrincipal` and the three cross-tenant JPA stores. Produces `@CrossTenant @ApplicationScoped` beans, each guarded by:
```java
if (!systemPrincipal.isCrossTenantAdmin())
    throw new IllegalStateException("QhorusSystemCurrentPrincipal.isCrossTenantAdmin() must return true — qhorus#260");
```

---

## Cross-Tenant Store Interfaces

Location: `api/src/main/java/io/casehub/qhorus/api/spi/`

```java
public interface CrossTenantChannelStore {
    List<Channel> listAll();
    Optional<Channel> findById(UUID id);
}

public interface CrossTenantMessageStore {
    List<Message> scan(MessageQuery query);
    int countByChannel(UUID channelId);
    List<String> distinctSendersByChannel(UUID channelId, MessageType excludedType);
    Optional<Message> findLastMessage(UUID channelId);
}

public interface CrossTenantCommitmentStore {
    void expireOverdue(Instant cutoff);
    List<Commitment> findOpenByChannel(UUID channelId);
}

public interface CrossTenantWatchdogStore {
    List<Watchdog> listAll();
}
```

**JPA implementations** — `JpaCrossTenant*Store` in `runtime/`. No `CurrentPrincipal` injection. No `WHERE tenancy_id = ?` clause. `@ApplicationScoped` — coexist with tenant-filtered stores.

**InMemory implementations** — `InMemoryCrossTenant*Store` in `testing/`. Return all entities. `@Alternative @Priority(1)`.

---

## Entity Changes

All four entities gain:
```java
@Column(name = "tenancy_id", nullable = false, updatable = false)
public String tenancyId;
```

`updatable = false` — tenancyId is immutable after creation. Never appears in any DTO, REST response, or MCP tool output.

---

## Tenant-Filtered JPA Stores (Read Path)

`JpaChannelStore`, `JpaMessageStore`, `JpaCommitmentStore`, `JpaWatchdogStore` each:

1. Inject `CurrentPrincipal`
2. Add `AND tenancyId = ?1` to every `find` / `scan` / `count` / `list` query using `currentPrincipal.tenancyId()`
3. `put()` does **not** call `currentPrincipal.tenancyId()` — persists `entity.tenancyId` as-is (already stamped by creation gate)

**`JpaChannelStore.findByName(String name)`** adds `AND tenancyId = ?` — name lookup is implicitly tenant-scoped. Two tenants with `general` channel find only their own.

**Reactive stores** (`ReactiveJpa*Store`) follow the same pattern. Capture `String tenancyId = currentPrincipal.tenancyId()` before entering the `Uni` pipeline (GE-20260528-55a526).

---

## MessageDispatch and MessageService

### `MessageDispatch` — 14th field

```java
public record MessageDispatch(
    UUID channelId, String sender, MessageType type, String content,
    String correlationId, Long inReplyTo, String artefactRefs, String target,
    UUID subjectId, UUID causedByEntryId, ActorType actorType, Instant deadline,
    String telemetry,
    String tenancyId) { ... }   // nullable — null = filled by MessageService
```

Builder gains `.tenancyId(String v)`. `build()` adds no validation — null is intentional for all HTTP callers.

### `MessageService.dispatch()` — tenancyId resolution

```java
@Inject CurrentPrincipal currentPrincipal;
@CrossTenant @Inject CrossTenantChannelStore crossTenantChannelStore;

public DispatchResult dispatch(MessageDispatch dispatch) {
    String effectiveTenancyId = dispatch.tenancyId() != null
        ? dispatch.tenancyId()
        : currentPrincipal.tenancyId();

    Channel channel = crossTenantChannelStore.findById(dispatch.channelId())
        .orElseThrow(() -> new IllegalArgumentException("Channel not found: " + dispatch.channelId()));

    if (!effectiveTenancyId.equals(channel.tenancyId)) {
        throw new IllegalArgumentException(
            "Cross-tenant dispatch rejected: caller tenant=" + effectiveTenancyId
            + ", channel tenant=" + channel.tenancyId);
    }

    // ... existing enforcement (paused, ACL, rate-limit) ...

    message.tenancyId = effectiveTenancyId;
    messageStore.put(message);

    // pass effective tenancyId to ledger via entity
    ledgerWriteService.record(dispatch, message, channel, ...);
}
```

Channel lookup uses `CrossTenantChannelStore` so the method works regardless of whether the caller has a CDI request context (handles watchdog path). The tenancyId equality check prevents HTTP callers from dispatching cross-tenant.

### `LedgerWriteService.record()`

Reads `message.tenancyId` (already stamped) to populate the ledger entry. No `CurrentPrincipal` injection — tenancyId flows through the entity across the `REQUIRES_NEW` boundary.

`MessageLedgerEntry` extends `LedgerEntry` via JOINED inheritance. `LedgerEntry` gains `tenancy_id` on the base `ledger_entry` table via casehub-ledger#127 — `MessageLedgerEntry` inherits it automatically. No separate field or migration needed in qhorus.

---

## ChannelService (Creation Gate)

`ChannelService.findOrCreate()` and `create()` stamp `channel.tenancyId = currentPrincipal.tenancyId()` before `channelStore.put()`. This is the only place `ChannelService` touches `CurrentPrincipal` — for entity creation, not for filtering. Filtering is entirely in the store.

---

## MessageReceivedEvent — SPI Break

`tenancyId` added as 3rd field (after `channelId`, per PP-20260601-e368ea ordering):

```java
// Before
public record MessageReceivedEvent(
    String channelName, UUID channelId,
    MessageType messageType, String senderId, String correlationId, String content)

// After
public record MessageReceivedEvent(
    String channelName, UUID channelId, String tenancyId,
    MessageType messageType, String senderId, String correlationId, String content)
```

`MessageObserverDispatcher` populates `tenancyId` from the resolved `effectiveTenancyId` in the dispatch.

**Consuming repos requiring migration:** claudony (`InProcessMessageBus`, `FleetMessageRelayObserver`), connectors, openclaw (`ChannelContextWindow`), clinical. Tracked in casehubio/qhorus#266.

---

## WatchdogEvaluationService

Redesigned to inject `@CrossTenant` variants of all stores:

```java
@CrossTenant @Inject CrossTenantChannelStore crossTenantChannelStore;
@CrossTenant @Inject CrossTenantMessageStore crossTenantMessageStore;
@CrossTenant @Inject CrossTenantCommitmentStore crossTenantCommitmentStore;
@CrossTenant @Inject CrossTenantWatchdogStore crossTenantWatchdogStore;
@Inject MessageService messageService;  // retained for alert dispatch
```

Alert dispatch uses `channel.tenancyId` explicitly:
```java
MessageDispatch alert = MessageDispatch.builder()
    .channelId(channel.id)
    .tenancyId(channel.tenancyId)   // GE-20260531-446fea — no CDI context in scheduler
    .sender("system:watchdog")
    .type(MessageType.EVENT)
    // ...
    .build();
messageService.dispatch(alert);
```

`WatchdogService.register()` stamps `watchdog.tenancyId = currentPrincipal.tenancyId()` at registration. `JpaWatchdogStore.listAll()` filters by tenant (for `list_watchdogs` MCP tool). `WatchdogEvaluationService` uses `CrossTenantWatchdogStore` — evaluates all tenants' watchdogs in each scheduler tick.

---

## ChannelGateway — Startup Recovery

`ChannelGateway.@Observes StartupEvent` currently calls `channelService.listAll()`. With tenant filtering active, this would only return the `DEFAULT_TENANT_ID` channels (the startup event runs outside a request context). Replace with the cross-tenant store directly.

`QhorusMcpTools.list_channels()` also calls `channelService.listAll()` — this call is **correct as-is**: it runs in an HTTP request context, so `JpaChannelStore` filters by the calling tenant's identity. Only the gateway startup path needs the cross-tenant replacement.

Replace in ChannelGateway:

```java
@CrossTenant @Inject CrossTenantChannelStore crossTenantChannelStore;

// in @Observes StartupEvent:
for (Channel ch : crossTenantChannelStore.listAll()) { ... }
```

---

## InMemory Stores

`InMemoryChannelStore`, `InMemoryMessageStore`, `InMemoryCommitmentStore`, `InMemoryWatchdogStore` — **no tenant filtering**. These are test-only `@Alternative @Priority(1)` beans. They remain single-tenant in semantics. Tenant isolation is tested at the JPA level only.

`put()` stamps `entity.tenancyId = TenancyConstants.DEFAULT_TENANT_ID` when `entity.tenancyId` is null — avoids GE-20260601-a35fb3 (null tenancyId silently returns null on equality checks in cross-tenant lookup).

---

## Testing Strategy

### Tenant isolation (`TenantIsolationTest` — `@QuarkusTest`)
Uses two distinct tenancyId strings. Mock `CurrentPrincipal` switches between them. Asserts:
- Channel created under tenant A invisible to tenant B's `scan()` and `findByName()`
- Message dispatched by tenant A invisible to tenant B's `scan()`
- Commitment opened by tenant A invisible to tenant B's `findOpenByObligor()`
- `findByName("general")` returns correct channel per tenant

### `CrossTenantProducerTest` (`@QuarkusTest`)
Injects `@CrossTenant` beans, asserts non-null. Verifies guard fires when `isCrossTenantAdmin()` returns false.

### `MessageDispatchTenancyTest` (`@QuarkusTest`)
- Normal HTTP path: null `dispatch.tenancyId()` → resolved from `CurrentPrincipal`
- Explicit system path: `dispatch.tenancyId()` non-null → used as-is
- Cross-tenant rejection: explicit tenancyId ≠ `channel.tenancyId` → `IllegalArgumentException`

### Observer tests
Existing `MessageDispatchIntegrationTest` updated to assert `event.tenancyId()` is non-null and matches the dispatching principal's tenancyId.

---

## Out-of-Scope — Tracked Issues

| Issue | Description |
|---|---|
| casehubio/qhorus#264 | Tenant-aware AgentCard (`/.well-known/agent-card.json`) |
| casehubio/qhorus#265 | A2A inbound endpoint tenant scoping |
| casehubio/qhorus#266 | `MessageReceivedEvent` migration in consuming repos |
| ~~casehubio/ledger#127~~ | ~~Ledger `LedgerEntry` tenancy scoping~~ — CLOSED (2026-06-09) |
| casehubio/work#256 | Work `WorkItem` tenancy scoping |

---

## PLATFORM.md Update Required

`MessageDispatch` builder description in the Capability Ownership table: update field count from 13 to 14 (adds `tenancyId`). No new cross-repo dependencies introduced.
