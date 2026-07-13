# CLUSTER-Scoped MessageObserver — Design Spec

**Issue:** #163
**Date:** 2026-07-13

## Problem

Qhorus has two parallel message delivery systems that serve distinct purposes:

| | MessageObserver | ChannelBackend |
|---|---|---|
| Discovery | CDI bean at startup | Runtime per-channel registration |
| Lifecycle | None (`@FunctionalInterface`, just `onMessage`) | `open()` / `post()` / `close()` |
| Direction | Unidirectional (observe only) | Bidirectional (Slack sends inbound, A2A streams back) |
| Filtering | `channels()` set on bean | Per-channel registry in ChannelGateway |
| Cross-node | Not implemented (`Scope.CLUSTER` unused) | `deliverRemote()` via pg_notify |
| Guarantee | Implicit best-effort | `DeliveryGuarantee` enum (BEST_EFFORT, AT_LEAST_ONCE) |
| Interface | `@FunctionalInterface` — one method | 5 methods + sub-interfaces |

**This split serves a real architectural purpose.** ChannelBackend = "I participate in this channel" — per-channel configuration, bidirectional communication, managed lifecycle (Slack, A2A, connectors). MessageObserver = "I observe what happens" — global, unidirectional, stateless (CDI events, analytics, notifications).

The problem is that `Scope.CLUSTER` on MessageObserver was designed but never wired up. The `MessageObserverDispatcher` ignores scope entirely — it fires all observers on the dispatching node only. `ChannelGateway.deliverRemote()` delivers to ChannelBackends on remote nodes but ignores observers.

This means observers are single-node only, which blocks three transport implementations that need the observer abstraction:

- **Kafka** — publish events to a Kafka topic for external consumers (analytics, clinical systems)
- **WebSocket** — push live events to connected browser clients (Claudony terminals)
- **Webhook** — HTTP POST callbacks to external systems

Kafka and Webhook work as LOCAL observers (fire once on the dispatching node, publish to their transport). WebSocket does not — clients connect to any node, so the observer must fire on ALL nodes where clients are connected. Without CLUSTER scope in the dispatcher, WebSocket cannot work correctly in a multi-node deployment.

## Solution

### Foundation: Implement CLUSTER Scope

Give `Scope` real dispatch semantics:

| Scope | Dispatching node | Remote nodes |
|-------|-----------------|--------------|
| LOCAL | fires | not fired |
| CLUSTER | fires | fires |

On the dispatching node, `MessageObserverDispatcher.dispatch()` fires all observers regardless of scope — both LOCAL and CLUSTER see the event. On remote nodes, `dispatchClusterOnly()` fires only CLUSTER-scoped observers. The net effect: LOCAL observers fire on exactly one node (the dispatching node); CLUSTER observers fire on every node in the cluster.

Both always see committed data — on the dispatching node because of JTA `afterCompletion(COMMITTED)`, on remote nodes because `deliverRemote()` reads from DB after the originating commit + pg_notify propagation.

**Both dispatch paths covered.** The scope-filtering change applies to both `MessageService` (blocking) and `ReactiveMessageService` (reactive), since both use `MessageObserverDispatcher.dispatch()`. The reactive path passes `null` for `tsr` (no JTA transaction), so observers fire synchronously — this is correct and preserved.

**LAST_WRITE overwrite path.** Both `MessageService` and `ReactiveMessageService` currently exclude `MessageObserverDispatcher.dispatch()` from the LAST_WRITE overwrite path (the comment reads: "an overwrite is a content update, not a new message event"). This exclusion must be removed: a LAST_WRITE overwrite IS content that connected clients need to see. Without observer dispatch on the overwrite path, the `broadcaster.broadcast()` call triggers `deliverRemote()` → `dispatchClusterOnly()` on remote nodes (firing CLUSTER observers), while the dispatching node fires no observers — creating node-asymmetric behavior. Fix: add `MessageObserverDispatcher.dispatch()` to the LAST_WRITE overwrite path in both services, after the message is saved and before the `afterCompletion` synchronization is registered.

### Three Transport Modules

All three transports are MessageObservers — the right abstraction for "when a message happens, do X." Each is a separate optional Maven module activated by classpath presence, following the existing pattern (`slack-channel/`, `connector-backend/`, `postgres-broadcaster/`).

Wire format: CloudEvents for all three. Already on the classpath (`casehub-platform-api → cloudevents-core`), already mapped by `QhorusCloudEventAdapter`, has formal Kafka and HTTP protocol bindings.

## Detailed Design

### 1. CloudEventMapper (runtime/gateway/)

Extract the `MessageReceivedEvent → CloudEvent` mapping from `QhorusCloudEventAdapter` into a shared utility:

```java
public final class CloudEventMapper {
    public static CloudEvent toCloudEvent(MessageReceivedEvent event, ObjectMapper mapper) { ... }
}
```

Mapping (unchanged from QhorusCloudEventAdapter):
- `type` → `io.casehub.qhorus.message.<messageType>` (lowercase)
- `source` → `/casehub-qhorus/channel/<channelId>`
- `subject` → `channel/<channelId>`
- `id` → random UUID
- `time` → `event.occurredAt()`
- `data` → JSON-serialised MessageReceivedEvent
- `tenancyid` extension → `event.tenancyId()` (omitted when null)

`QhorusCloudEventAdapter` becomes a thin wrapper: `@ObservesAsync MessageReceivedEvent → CloudEventMapper.toCloudEvent() → Event<CloudEvent>.fireAsync()`.

### 2. MessageObserverDispatcher Scope Filtering

Add a scope-aware dispatch entry point:

- `dispatch(channelName, channelId, tenancyId, message, handles, tsr)` — existing, fires ALL observers (unchanged, called by MessageService)
- `dispatchClusterOnly(channelName, channelId, tenancyId, message, handles)` — new, fires only observers where `scope() == CLUSTER`. Accepts a `Message` and constructs `MessageReceivedEvent` internally, consistent with `dispatch()`.

The `dispatchClusterOnly` variant has no `tsr` parameter — `deliverRemote()` runs outside a JTA transaction on a virtual thread. Synchronous dispatch is correct there.

Channel filtering (`channels()` set) and EVENT content nulling apply identically in both paths — handled by the existing dispatch logic.

**Package visibility.** `MessageObserverDispatcher` is `final class` (package-private) in `io.casehub.qhorus.runtime.message` — all its static methods are package-private. This is correct encapsulation: the dispatcher is an implementation detail. `ChannelGateway` (in `runtime.gateway`) cannot call it directly.

External access is through a new public method on `MessageService`:

```java
public void dispatchClusterObservers(String channelName, UUID channelId,
                                      String tenancyId, Message message) {
    MessageObserverDispatcher.dispatchClusterOnly(
            channelName, channelId, tenancyId, message, observers.handles());
}
```

`ChannelGateway` already injects `MessageService` — no new dependency. `MessageService` already owns the `Instance<MessageObserver> observers` reference and is the established dispatch entry point for both blocking and reactive paths. Adding a cluster-only variant is consistent with that responsibility.

### 3. ChannelGateway.deliverRemote() Enhancement

After backend delivery in `deliverRemote()`, call `messageService.dispatchClusterObservers()` with the DB-read `Message` and `Channel` metadata. `MessageService` delegates to the package-private `MessageObserverDispatcher.dispatchClusterOnly()`, which constructs `MessageReceivedEvent` internally — consistent with the existing `dispatch()` pattern. No `MessageReceivedEvent` construction in `deliverRemote()` itself.

The fields map directly from the existing `Message` + `Channel` data already loaded by `deliverRemote()`:

| MessageReceivedEvent field | Source |
|---|---|
| channelName | `ch.name()` |
| channelId | `channelId` parameter |
| tenancyId | `msg.tenancyId()` |
| messageType | `msg.messageType()` |
| senderId | `msg.sender()` |
| correlationId | `msg.correlationId()` |
| occurredAt | `msg.createdAt()` |
| content | `msg.content()` (null for EVENT) |
| topic | `msg.topic()` |

### 4. Kafka Observer Module

```
kafka-observer/
├── pom.xml                    — casehub-qhorus-api, casehub-qhorus, quarkus-messaging-kafka, cloudevents-kafka
└── src/main/java/io/casehub/qhorus/kafka/
    ├── KafkaMessageObserver.java
    └── KafkaObserverConfig.java
```

**KafkaMessageObserver** — `@ApplicationScoped`, `scope() = LOCAL`.

On `onMessage()`: `CloudEventMapper.toCloudEvent()` → publish via SmallRye Reactive Messaging `Emitter<CloudEvent>`. CloudEvents Kafka protocol binding handles serialization (type/source as Kafka headers, data as value).

- **Topic:** single configurable topic, default `qhorus-messages`
- **Kafka key:** `channelId` (UUID string) — natural partitioning by channel, preserves per-channel ordering
- **Channel filtering:** overrides `channels()` with the set from `casehub.qhorus.kafka.channels` config (read at startup, immutable at runtime); empty = all channels
- **Stateless:** no JPA entities, no Flyway migration

```properties
casehub.qhorus.kafka.topic=qhorus-messages
casehub.qhorus.kafka.channels=             # empty = all channels
```

Consumers filter by CloudEvent `source` (channel), `type` (message type), or `tenancyid` extension as needed.

### 5. WebSocket Observer Module

```
websocket-observer/
├── pom.xml                    — casehub-qhorus-api, casehub-qhorus, quarkus-websockets-next, cloudevents-core
└── src/main/java/io/casehub/qhorus/websocket/
    ├── WebSocketMessageObserver.java
    ├── ChannelWebSocketEndpoint.java
    └── WebSocketObserverConfig.java
```

Two components, one concern:

**ChannelWebSocketEndpoint** — Quarkus WebSockets Next `@WebSocket` endpoint. Path-based subscription: clients connect to `/qhorus/ws/channels/{channelId}` to observe a specific channel. The path uses `channelId` (UUID), not `channelName`, because channel names are scoped per tenancy — two tenants can have channels with the same name (`CrossTenantChannelStore.findByNameAndTenancy` confirms this). Using `channelId` is unambiguous, tenant-safe, and consistent with how `ChannelBackend` operates (always by channelId). No custom subscription protocol — the path IS the subscription.

- `@OnOpen`: add connection to registry keyed by channelId
- `@OnClose`: remove connection from registry

**WebSocketMessageObserver** — `@ApplicationScoped`, `scope() = CLUSTER`.

On `onMessage()`: look up connections subscribed to `event.channelId()` in the registry, map event → CloudEvent JSON via `CloudEventMapper`, push to each connection via `connection.sendText()`. The CloudEvent payload includes `channelName` for display purposes. Failed sends log and skip.

- **Connection registry:** `ConcurrentHashMap<UUID, Set<WebSocketConnection>>` keyed by channel ID. In-memory, per-node, ephemeral. Lost on restart — clients reconnect and resubscribe. Catch-up mechanism for reconnecting clients tracked in #346.
- **Cross-node:** scope CLUSTER means the observer fires on ALL nodes. A message dispatched on Node B triggers `deliverRemote()` on Node A, which fires CLUSTER observers, which pushes to Node A's WebSocket clients.
- **No authentication in the module.** The WebSocket upgrade request goes through the Quarkus HTTP pipeline — whatever security the consumer app configures applies.
- **Stateless:** no JPA entities, no Flyway migration

```properties
casehub.qhorus.websocket.path=/qhorus/ws    # base path
```

### 6. Webhook Observer Module

```
webhook-observer/
├── pom.xml                    — casehub-qhorus-api, casehub-qhorus, cloudevents-core, quarkus-vertx
└── src/main/java/io/casehub/qhorus/webhook/
    ├── WebhookMessageObserver.java
    ├── WebhookRegistry.java
    ├── WebhookRegistryResource.java
    └── WebhookObserverConfig.java
```

**WebhookMessageObserver** — `@ApplicationScoped`, `scope() = CLUSTER`.

On `onMessage()`: look up registered webhooks for `event.channelId()` (plus global webhooks), map event → CloudEvent JSON via `CloudEventMapper`, POST to each URL using Vert.x `WebClient` on a virtual thread. Fire-and-forget — failures logged, not retried.

No retry by design: retry with backoff requires persistent state (failed deliveries, retry schedule). That's the delivery pump's job for ChannelBackends with AT_LEAST_ONCE. An observer is a notification mechanism. If guaranteed delivery to an HTTP endpoint is needed, implement it as a ChannelBackend.

**WebhookRegistry** — `@ApplicationScoped`. `ConcurrentHashMap<UUID, Set<WebhookRegistration>>` keyed by channel ID for channel-specific webhooks, plus a separate `Set<WebhookRegistration>` for global webhooks (registered with `channelId = null`). Observer lookup is the union of channel-specific and global sets. Channel IDs are used instead of channel names for the same tenant-isolation reason as the WebSocket module (R1-02) — channel names are not globally unique. Each registration: callback URL, optional secret (HMAC-SHA256 signature), optional custom headers. In-memory — registrations lost on restart. Persistent registrations (JPA) tracked in #345.

**Duplicate delivery constraint.** With CLUSTER scope and per-node in-memory registrations, the same webhook URL registered on multiple nodes receives duplicate deliveries — the dispatching node fires via `dispatch()` and remote nodes fire via `dispatchClusterOnly()`, each checking their own registry. Mitigation: register each webhook URL on exactly one node. The persistent registry (#345) eliminates this by moving to a shared database-backed store where deduplication is enforced by the registration's unique ID.

**WebhookRegistryResource** — JAX-RS REST API for dynamic registration:

```
POST   /qhorus/webhooks                        — register (channelId, url, secret?, headers?); omit channelId for global
DELETE /qhorus/webhooks/{id}                    — deregister
GET    /qhorus/webhooks                         — list all
GET    /qhorus/webhooks?channelId={uuid}        — list for channel
```

Dynamic registration follows the same pattern as `SlackBindingResource`.

**Payload:** CloudEvents HTTP binding — `Content-Type: application/cloudevents+json`. If a secret is registered, `X-Qhorus-Signature` header carries HMAC-SHA256 of the body for receiver verification.

```properties
casehub.qhorus.webhook.path=/qhorus/webhooks    # REST API base path
casehub.qhorus.webhook.timeout-ms=5000          # per-POST timeout
```

## Build Order

1. Foundation (runtime) — CloudEventMapper extraction, scope-aware dispatcher, deliverRemote enhancement
2. Kafka observer — simplest transport, validates the observer pattern
3. Webhook observer — CLUSTER scope, adds registration API
4. WebSocket observer — validates CLUSTER scope in a real transport

## Documentation Update

Update `docs/messaging-architecture.md` "What Ships When" table to reflect that CLUSTER scope is now implemented and three transport modules ship. Change `KafkaMessageBus`, `WebSocketMessageBus`, webhook impl from `⬜ future` to `🔧 qhorus#163`. Update the "Transport Scope" section to document CLUSTER scope semantics.

## Testing Strategy

**Foundation tests (runtime/):**
- Unit: `MessageObserverDispatcher` scope filtering — CLUSTER-only dispatch skips LOCAL observers
- Unit: `CloudEventMapper` produces correct CloudEvent structure
- Unit: `MessageService` LAST_WRITE overwrite fires observers — verify `MessageObserverDispatcher.dispatch()` is called on the overwrite path, not just on first write
- Unit: `ReactiveMessageService` LAST_WRITE overwrite fires observers — same verification for the reactive path
- Integration: `ChannelGateway.deliverRemote()` fires CLUSTER observers via `messageService.dispatchClusterObservers()` (CDI-free unit test with mock observers)

**Per-module tests:**
- Kafka: unit test with mock Emitter, verify CloudEvent published with correct key/topic/payload
- WebSocket: unit test with mock WebSocketConnection, verify push on onMessage; integration test with Quarkus WebSockets test client
- Webhook: unit test with WireMock, verify POST payload and HMAC signature; registry CRUD tests

All module tests are CI-safe — no external infrastructure. Kafka tests mock the Emitter. WebSocket tests use the Quarkus in-memory WebSocket test support. Webhook tests use WireMock.
