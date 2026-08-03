# casehub-qhorus -- Consumer Guide

> Agent communication mesh and governance methodology for multi-agent AI systems.

**GitHub:** [casehubio/qhorus](https://github.com/casehubio/qhorus)
**Tier:** Foundation

---

## Purpose

Qhorus gives every agent interaction the formal status of an accountable act -- grounded in speech act theory, deontic logic, defeasible reasoning, and social commitment semantics. The LLM reasons; Qhorus enforces, records, and derives.

Any Quarkus app adds `io.casehub:casehub-qhorus` as a dependency and its agents immediately get typed channels, typed messages, shared data, an instance registry, normative audit ledger, channel summaries, topics, reactions, presence, membership, watchdog alerting, a REST API for channel CRUD, and a channel gateway with backend-agnostic fan-out.

---

## Modules to Depend On

| Module | ArtifactId | When to use |
|--------|-----------|-------------|
| `api` | `casehub-qhorus-api` | Always -- SPIs, domain records, service facades, reader interfaces |
| `runtime` | `casehub-qhorus` | Always -- core services, MCP tools, REST API, ledger integration |
| `persistence-memory` | `casehub-qhorus-persistence-memory` | In-memory stores for tests and zero-config ephemeral installs |
| `testing` | `casehub-qhorus-testing` | Test utilities (`RecordingChannelBackend`, `MessageLedgerEntryTestFactory`) |
| `connector-backend` | `casehub-qhorus-connector-backend` | Optional -- bridges casehub-connectors into Qhorus channels |
| `connectors` | `casehub-qhorus-connectors` | Optional -- routes `WatchdogAlertEvent` to casehub-connectors for external alert dispatch |
| `slack-channel` | `casehub-qhorus-slack-channel` | Optional -- Slack channel backend |
| `kafka-observer` | `casehub-qhorus-kafka-observer` | Optional -- Kafka message observer |
| `websocket-observer` | `casehub-qhorus-websocket-observer` | Optional -- WebSocket real-time push with catch-up replay |
| `webhook-observer` | `casehub-qhorus-webhook-observer` | Optional -- HTTP POST webhook callbacks with HMAC-SHA256 signing |
| `notification-bridge` | `casehub-qhorus-notification-bridge` | Optional -- commitment lifecycle to platform subscription engine |
| `postgres-broadcaster` | `casehub-qhorus-postgres-broadcaster` | Optional -- cross-node delivery via PostgreSQL LISTEN/NOTIFY |

Optional modules activate by classpath presence -- no configuration needed beyond adding the dependency.

---

## Key Abstractions

### Channels

Typed communication channels with configurable delivery semantics, access control, and lifecycle management.

| Semantic | Behaviour |
|----------|-----------|
| `APPEND` | Ordered message log (default) |
| `COLLECT` | Accumulates contributions |
| `BARRIER` | Blocks until all contributors respond |
| `EPHEMERAL` | Messages not persisted after delivery |
| `LAST_WRITE` | Latest value wins (version-aware, AT_LEAST_ONCE delivery) |

Channel fields (all nullable unless specified):

| Field | Purpose |
|-------|---------|
| `name` | Unique identifier; validated by `ChannelSlugValidator` |
| `description` | Human-readable channel description |
| `semantic` | `ChannelSemantic` enum; defaults to `APPEND` |
| `barrierContributors` | Required contributors for `BARRIER` channels |
| `allowedWriters` | ACL -- which instances can write (empty = unrestricted) |
| `adminInstances` | Administrative access list |
| `reviewerInstances` | Peer review participants |
| `allowedTypes` / `deniedTypes` | Message type filtering; `deniedTypes` wins when both set |
| `rateLimitPerChannel` / `rateLimitPerInstance` | Rate limits (null = unrestricted) |
| `protocols` | Named protocol enforcement rules (e.g. `REQUEST_RESPONSE`) |
| `protocolParticipants` | Agents subject to protocol enforcement |
| `trackDelivery` | Opt-in per-participant delivery tracking via cursor extension |
| `paused` | When true, dispatch gate rejects all writes |
| `spaceId` | Parent space for organizational hierarchy |
| `autoCreated` | Whether the channel was auto-created via `findOrCreate` |

**Spaces** provide recursive channel hierarchy (max depth 10) for grouping related channels with parent/child nesting via `Space(id, name, description, parentSpaceId, tenancyId, createdAt)`.

### Message Types (Speech-Act Taxonomy -- ADR-0005)

| Type | Intent | Creates obligation | Terminal? |
|------|--------|--------------------|-----------|
| `QUERY` | Information request | Yes -- RESPONSE or DECLINE required | No |
| `COMMAND` | Action request | Yes -- DONE, FAILURE, or DECLINE required | No |
| `RESPONSE` | Answers a QUERY | No (discharges QUERY obligation) | Yes (for that QUERY) |
| `STATUS` | Progress update on open COMMAND | No | No |
| `DECLINE` | Refuse a QUERY or COMMAND | No (discharges obligation) | Yes |
| `HANDOFF` | Delegate COMMAND to another agent | Transfers obligation | No |
| `DONE` | Successful COMMAND completion | No | Yes |
| `FAILURE` | Failed COMMAND | No | Yes |
| `EVENT` | Telemetry / observer signal | No | N/A -- excluded from agent context |

Utility methods on `MessageType`:
- `isAgentVisible()` -- true for all except `EVENT`
- `requiresCorrelationId()` -- true for `QUERY` and `COMMAND`
- `requiresContent()` -- true for `DECLINE` and `FAILURE`
- `requiresTarget()` -- true for `HANDOFF`
- `isTerminal()` -- true for `HANDOFF`, `DONE`, `FAILURE`

### Messages

`Message` is the full domain record with 18 fields:

| Field | Type | Notes |
|-------|------|-------|
| `id` | `Long` | Sequence PK for ordering |
| `channelId` | `UUID` | FK to channel |
| `sender` | `String` | Instance ID of the sender |
| `messageType` | `MessageType` | Speech act type |
| `actorType` | `ActorType` | `AGENT`, `HUMAN`, or `SYSTEM` |
| `content` | `String` | Message body (null for EVENT) |
| `correlationId` | `String` | Groups related messages into a conversation thread |
| `inReplyTo` | `Long` | Parent message ID for threading |
| `replyCount` | `int` | Denormalized reply count |
| `artefactRefs` | `List<ArtefactRef>` | Typed document references |
| `target` | `String` | Named recipient or capability tag (required for HANDOFF) |
| `topic` | `String` | Sub-conversation within channel; defaults to `"general"` |
| `commitmentId` | `UUID` | Associated commitment |
| `deadline` | `Instant` | Obligation deadline |
| `acknowledgedAt` | `Instant` | When STATUS was received |
| `version` | `int` | Optimistic locking version |
| `tenancyId` | `String` | Tenant isolation key |
| `createdAt` | `Instant` | Persistence timestamp |

`MessageView` is the read-side DTO used in projections and scan results -- a subset of `Message` with 14 fields.

### ArtefactRef

Typed, anchored document references attached to messages:

```java
record ArtefactRef(String uri, ArtefactType type, String label, SelectionScope scope)
```

`ArtefactType` values: `DOCUMENT`, `CODE`, `CASE`, `WORK_ITEM`, `CHANNEL`, `MESSAGE`, `EXTERNAL`, `DEBATE`.

`SelectionScope` controls how much of the artefact is referenced (full document, fragment, etc.).

### Topics

Named persistent sub-conversations within channels. Every message belongs to a topic; defaults to `"general"` if not specified. Topic names are capped at 200 characters.

`Topic(id, channelId, name, resolved, tenancyId, createdAt)` -- `resolved` marks a topic as concluded.

`TopicSummary` provides aggregated stats per topic (message count, last activity).

### Reactions

Lightweight emoji reactions on messages:

`Reaction(id, messageId, emoji, reactorId, tenancyId, createdAt)` -- one reaction per user per emoji per message.

`ReactionGroup` provides aggregated counts per emoji.

### Dispatch Gate

All channel writes flow through a single enforcement gate: `MessageService.dispatch(MessageDispatch)`.

Pipeline (in order):
1. **Paused check** -- rejects if channel is paused
2. **`AllowedWritersPolicy`** -- ACL enforcement
3. **`RateLimiter`** -- per-channel and per-instance rate limiting
4. **`ObligorTrustPolicy` SPI** -- trust threshold for COMMAND dispatch
5. **`MessageTypePolicy`** -- `allowedTypes`/`deniedTypes` enforcement
6. **`CorrelationIntegrityChecker`** -- advisory: validates `inReplyTo` and `correlationId` consistency
7. **`ProtocolEvaluation`** -- advisory: channel protocol enforcement via `ProtocolRegistry`
8. **LAST_WRITE overwrite** -- version-aware overwrite semantics for `LAST_WRITE` channels
9. **`LedgerWriteService.record()`** -- tamper-evident ledger entry
10. **`ChannelGateway.fanOut()`** -- backend delivery and observer notification

There is no bypass path.

#### MessageDispatch Builder

`MessageDispatch` builds via `MessageDispatch.builder()` with required fields enforced at `build()`:

| Field | Required | Notes |
|-------|----------|-------|
| `channelId` | Yes | Target channel UUID |
| `sender` | Yes | Instance ID |
| `type` | Yes | `MessageType` enum |
| `actorType` | Yes | `ActorType` enum |
| `content` | Conditional | Must be null for EVENT; required for DECLINE, FAILURE |
| `correlationId` | Conditional | Required for RESPONSE, DONE, FAILURE, DECLINE, HANDOFF |
| `inReplyTo` | Conditional | Required for RESPONSE, DONE, FAILURE, DECLINE, HANDOFF |
| `target` | Conditional | Required for HANDOFF |
| `topic` | No | Defaults to `"general"` if blank/null; max 200 chars |
| `deadline` | No | Propagated to Message and Commitment |
| `telemetry` | No | JSON telemetry data for EVENT messages |
| `artefactRefs` | No | List of `ArtefactRef` typed document references |
| `subjectId` | No | Explicit ledger subject ID |
| `causedByEntryId` | No | Explicit ledger causal entry |
| `tenancyId` | No | Tenant isolation key |

#### DispatchResult

Returned from every successful dispatch:

```java
record DispatchResult(
    Long messageId, UUID channelId, String sender, MessageType type,
    String correlationId, Long inReplyTo, List<ArtefactRef> artefactRefs,
    String target, UUID ledgerEntryId, UUID subjectId, UUID causedByEntryId,
    int parentReplyCount, List<String> advisories)
```

`advisories` carries non-fatal warnings from `CorrelationIntegrityChecker` and `ProtocolEvaluation`.

### Commitment Lifecycle

Obligations created by QUERY and COMMAND messages follow a state machine:

```
OPEN --> ACKNOWLEDGED --> FULFILLED
                     --> FAILED
                     --> DECLINED
                     --> DELEGATED (via HANDOFF)
                     --> EXPIRED (deadline enforcement)
```

`Commitment` record fields: `id`, `correlationId`, `channelId`, `messageType`, `requester`, `obligor`, `state`, `expiresAt`, `acknowledgedAt`, `resolvedAt`, `delegatedTo`, `parentCommitmentId`, `tenancyId`, `createdAt`.

`CommitmentState` has utility methods:
- `isTerminal()` -- true for FULFILLED, DECLINED, FAILED, DELEGATED, EXPIRED
- `isActive()` -- true for OPEN, ACKNOWLEDGED

CDI events fired on state transitions:
- `CommitmentDeclinedEvent` -- when a commitment transitions to DECLINED
- `CommitmentExpiredEvent` -- when deadline-based expiry fires
- `CommitmentStateChangedEvent` -- general state transition notification

### Channel Summary

Per-channel maintained summary with SPI hook for LLM-generated summaries.

`ChannelSummary` record: `id`, `channelId`, `content`, `annotations` (key-value metadata map), `updatedAt`, `updatedBy`, `lastUpdatedMessageId`, `updateAfterMessages`, `updateAfterSeconds`, `tenancyId`.

Implement `SummaryUpdateHook` to generate summaries:

```java
@FunctionalInterface
public interface SummaryUpdateHook {
    SummaryResult update(SummaryUpdateContext context);
}
```

`SummaryUpdateContext` provides: `channelId`, `channelName`, `tenancyId`, `previousResult`, `lastUpdatedMessageId`, `messagesSinceLastUpdate`, `recentMessages`, `messageQuery` (function for ad-hoc queries).

`SummaryResult(text, annotations)` carries the summary text and optional key-value annotations that round-trip through storage.

The runtime ships `NoOpSummaryUpdateHook` as default. Override with `@Alternative @Priority(1)`.

### Service Facades (Consumer-Facing)

Consumers interact through typed service facade interfaces in `api/`:

#### Mutating Facades

| Interface | Package | Purpose |
|-----------|---------|---------|
| `ChannelManager` | `api/channel/` | Channel lifecycle (create, findOrCreate, delete, pause, resume) and settings (type constraints, rate limits, ACLs, protocols, reviewers) |
| `MessageDispatcher` | `api/message/` | `dispatch(MessageDispatch)` -- single entry point for all message sends |
| `ConsumerMessaging` | `api/message/` | Extends `MessageDispatcher` with history queries: `history()`, `historyBySender()`, `findById()`, `findByCorrelationId()`, `findAllByCorrelationId()` |
| `TopicManager` | `api/channel/` | Topic CRUD: `create()`, `resolve()`, `unresolve()`, `rename()`, `merge()`, `listTopics()` |
| `MembershipManager` | `api/channel/` | Channel membership: `join()`, `leave()`, `setRole()`, `updateLastReadMessageId()` |
| `PresenceTracker` | `api/channel/` | Presence management: `heartbeat()`, `getPresence()`, `getChannelPresence()`, `setOffline()` |
| `ReactionManager` | `api/channel/` | Message reactions: `react()`, `unreact()` |

#### Read-Only Facades (Reader Interfaces)

| Interface | Package | Purpose |
|-----------|---------|---------|
| `ChannelReader` | `api/channel/` | `findById()`, `findByName()`, `findByNamePrefix()`, `listAll()`, `scan(ChannelQuery)`, `findByIds()` |
| `MessageReader` | `api/store/` | `find()`, `scan(MessageQuery)`, `countByChannel()`, `count()`, `countAllByChannel()`, `distinctSendersByChannel()`, `findLastMessage()`, `findRecent()` |
| `CommitmentReader` | `api/store/` | `findById()`, `findByCorrelationId()`, `findAllByCorrelationId()`, `findByIds()`, `findOpenByObligor()`, `findOpenByRequester()`, `findByState()`, `findByChannel()`, `findOpenByChannelId()`, `findExpiredBefore()`, `findAllOpen()` |
| `MembershipReader` | `api/store/` | `find()`, `findByChannel()`, `findByMember()` |
| `TopicReader` | `api/store/` | `find()`, `findById()`, `findByChannel()` |
| `ReactionReader` | `api/store/` | `findByMessage()`, `findByMessages()` (batch) |

#### Query Types

All reader interfaces accept structured query objects:

| Query | Fields |
|-------|--------|
| `ChannelQuery` | `namePrefix`, `spaceId`, `paused` |
| `MessageQuery` | `channelId`, `afterId`, `beforeId`, `sender`, `type`, `topic`, `limit`, `descending`, `includeEvents` |
| `DataQuery` | Key, creator, completeness filters |
| `InstanceQuery` | Status, capability filters |
| `WatchdogQuery` | Condition type, target name, tenancy filters |

### Channel Projection SPI

Left-fold SPI over channel message history. Implement `ChannelProjection<S>` to derive deterministic read-models (vote tallies, review manifests, digests) without scanning raw messages on every read.

```java
public interface ChannelProjection<S> {
    S identity();                          // fresh empty state
    S apply(S state, MessageView message); // fold one message
}
```

- `identity()` must return a fresh instance on every call (no shared singletons)
- `apply()` must be pure -- no side effects, no thread-local access
- Return `state` unchanged for messages this projection ignores; never return null

`ProjectionResult<S>(state, lastMessageId)` carries the materialised state and a cursor. Pass `ProjectionResult` back to the incremental `project()` overload to resume without full rescan.

**Named projections:** Implement `RenderableProjection<S>` (extends `ChannelProjection<S>`) with `projectionName()` and `render(ProjectionResult<S>)` to register in `ProjectionRegistry` for use via `project_channel` MCP tool.

Topic-aware projections: `project_channel` MCP tool accepts optional `topic` and `max_messages` parameters.

### Store SPIs

Store interfaces in `api/store/` provide data access for all domain types. Blocking implementations in `runtime/store/jpa/` use JPA with Panache.

| Store Interface | Domain | Notes |
|-----------------|--------|-------|
| `ChannelStore` | Channels | Full CRUD |
| `ChannelBindingStore` | Connector bindings | Inbound/outbound connector mapping |
| `ChannelMembershipStore` | Membership | Extends `MembershipReader` |
| `ChannelSummaryStore` | Summaries | Per-channel summary storage |
| `CommitmentStore` | Commitments | Extends `CommitmentReader` |
| `MessageStore` | Messages | Extends `MessageReader` |
| `InstanceStore` | Instances | Agent registry |
| `DataStore` | Shared data | Key-value with artefact claims |
| `WatchdogStore` | Watchdogs | Condition monitoring |
| `SpaceStore` | Spaces | Hierarchical grouping |
| `TopicStore` | Topics | Extends `TopicReader` |
| `ReactionStore` | Reactions | Extends `ReactionReader` |
| `DeliveryCursorStore` | Delivery cursors | AT_LEAST_ONCE tracking |

Cross-tenant variants exist for administrative queries that span tenants: `CrossTenantChannelStore`, `CrossTenantMessageStore`, `CrossTenantCommitmentStore`, `CrossTenantWatchdogStore`, `CrossTenantChannelSummaryStore`.

### Channel Gateway

Outbound messages route through a channel backend SPI. Three backend types form a sealed hierarchy:

| Interface | Cardinality | Actor type | Inbound | Failure mode |
|-----------|-------------|------------|---------|-------------|
| `AgentChannelBackend` | Always registered (default) | `AGENT` | N/A | Fatal -- exception surfaces to caller |
| `HumanParticipatingChannelBackend` | At most one per channel | `HUMAN` | Full speech act via `InboundNormaliser` | Non-fatal -- logged and continued |
| `HumanObserverChannelBackend` | Unlimited per channel | `HUMAN` | Capped to EVENT | Non-fatal -- logged and continued |

All backends implement `ChannelBackend`:
```java
public interface ChannelBackend {
    String backendId();
    ActorType actorType();
    void open(ChannelRef channel, Map<String, String> metadata);
    void post(ChannelRef channel, OutboundMessage message);
    void close(ChannelRef channel);
    default DeliveryGuarantee deliveryGuarantee() { return DeliveryGuarantee.BEST_EFFORT; }
}
```

`DeliveryGuarantee`: `BEST_EFFORT` (fire-and-forget) or `AT_LEAST_ONCE` (cursor-tracked, reconciliation-backed).

`HumanParticipatingChannelBackend` supports per-channel `InboundNormaliser` via `normaliserFor(UUID channelId)` for type inference.

**MessageObserver SPI:** `@FunctionalInterface` -- `onMessage(MessageReceivedEvent)` with `Scope.LOCAL` (in-JVM) or `Scope.CLUSTER` (cross-node). Optional `channels()` method for channel name filtering.

`MessageReceivedEvent` record: `messageId`, `channelName`, `channelId`, `tenancyId`, `messageType`, `senderId`, `target`, `actorType`, `correlationId`, `occurredAt`, `content` (null for EVENT), `topic`.

**Important:** do not query qhorus message state in observer implementations. The dispatcher fires before the enclosing transaction commits; the event payload is self-contained.

`ChannelInitialisedEvent(channelId, channelName)` fires on channel creation and startup recovery -- external backends observe this to re-register without implementing their own restart logic.

`ChannelClosedEvent(channelId, channelName)` fires when a channel is closed.

### Watchdog System

Configurable condition monitoring with 11 watchdog condition types:

| Condition | Context Record | What it detects |
|-----------|---------------|-----------------|
| `BARRIER_STUCK` | `BarrierStuckContext` | BARRIER channel waiting too long for contributors |
| `APPROVAL_PENDING` | `ApprovalPendingContext` | Approvals exceeding count/age thresholds |
| `AGENT_STALE` | `AgentStaleContext` | Agents not heartbeating |
| `CHANNEL_IDLE` | `ChannelIdleContext` | Channels with no activity |
| `QUEUE_DEPTH` | `QueueDepthContext` | Message backlog exceeding threshold |
| `CONTEXT_PRESSURE` | `ContextPressureContext` | Agent context window usage from telemetry |
| `LOOP_DETECTED` | `LoopDetectedContext` | Repeated similar messages (Jaccard similarity) |
| `OBLIGATION_FAN_OUT` | `ObligationFanOutContext` | Stale open obligations accumulating |
| `CONVERSATION_STALL` | `ConversationStallContext` | Correlations with no progress |
| `ECHO_CHAMBER` | `EchoChamberContext` | Participants echoing similar content |
| `CIRCULAR_DELEGATION` | `CircularDelegationContext` | HANDOFF chain forming a cycle |

`Watchdog` record: `id`, `conditionType`, `targetName`, `thresholdSeconds`, `thresholdCount`, `similarityPct`, `notificationChannel`, `createdBy`, `tenancyId`, `createdAt`, `lastFiredAt`.

`WatchdogAlertEvent` fires via CDI async when a condition triggers. The `WatchdogAlertRouter` SPI determines where alerts are delivered via `AlertDeliveryTarget(connectorId, destination)`.

### MCP Tool Surface

Qhorus exposes MCP tools scoped to `@McpServer("qhorus")` across capability groups: instance management, channel management, backend management, messaging, shared data, commitments, normative ledger queries, spaces, topics, presence, membership, reactions, projections, and watchdogs.

### REST API

`ChannelResource` at `/api/channels` provides HTTP endpoints for channel CRUD:

| Method | Path | Purpose |
|--------|------|---------|
| `POST` | `/api/channels` | Create channel (accepts `CreateChannelRequest` JSON) |
| `GET` | `/api/channels` | List channels (optional `prefix`, `spaceId`, `paused` query params) |
| `GET` | `/api/channels/{id}` | Get channel by UUID or name |
| `DELETE` | `/api/channels/{id}` | Delete channel (optional `force` query param) |
| `POST` | `/api/channels/{id}/pause` | Pause channel (rejects all writes) |
| `POST` | `/api/channels/{id}/resume` | Resume paused channel |
| `PUT` | `/api/channels/{id}/allowed-writers` | Set writer ACL |
| `PUT` | `/api/channels/{id}/admin-instances` | Set admin instances |
| `PUT` | `/api/channels/{id}/reviewer-instances` | Set reviewer instances |
| `PUT` | `/api/channels/{id}/type-constraints` | Set allowed/denied message types |
| `PUT` | `/api/channels/{id}/rate-limits` | Set rate limits |
| `PUT` | `/api/channels/{id}/protocols` | Set protocol enforcement |
| `PUT` | `/api/channels/{id}/protocol-participants` | Set protocol participants |
| `PUT` | `/api/channels/{id}/delivery-tracking` | Enable/disable delivery tracking |

Channel resolution supports both UUID and name lookups in the `{id}` path parameter.

### External APIs

- `GET /.well-known/agent-card.json` -- A2A ecosystem discovery
- `POST /a2a/message:send` -- A2A-compatible message receive endpoint
- `GET /a2a/tasks/{id}/stream` -- SSE streaming with keepalive events

A2A endpoints are opt-in via `casehub.qhorus.a2a.enabled=true`.

---

## Configuration

### Named Datasource Requirement

Qhorus always runs on a named `qhorus` datasource. Never share it with domain tables.

```properties
quarkus.datasource.qhorus.db-kind=postgresql
quarkus.datasource.qhorus.jdbc.url=jdbc:postgresql://localhost:5432/qhorus
```

### JPA Entity Registration for Optional Modules

Optional modules that ship JPA entities (e.g. `casehub-qhorus-slack-channel`) must have their package registered in `quarkus.hibernate-orm.qhorus.packages` by the consumer. The default scan covers only `io.casehub.qhorus.runtime` and `io.casehub.ledger.runtime`.

### Key Configuration Properties

| Property | Default | Purpose |
|----------|---------|---------|
| `casehub.qhorus.commitment.min-obligor-trust` | `0.0` | Trust threshold for COMMAND dispatch (0.0 = disabled) |
| `casehub.qhorus.commitment.default-query-deadline` | absent | Default deadline for QUERYs without explicit deadline |
| `casehub.qhorus.a2a.enabled` | `false` | Enable A2A REST endpoints |
| `casehub.qhorus.a2a.sse.heartbeat-interval-seconds` | `15` | SSE keepalive interval |
| `casehub.qhorus.a2a.sse.max-duration-seconds` | `1800` | SSE max stream duration |
| `casehub.qhorus.watchdog.enabled` | `false` | Enable watchdog evaluation scheduler |
| `casehub.qhorus.watchdog.check-interval-seconds` | `60` | Watchdog evaluation interval |
| `casehub.qhorus.watchdog.alert.endpoints` | empty | Alert dispatch targets (connectorId + destination) |
| `casehub.qhorus.delivery.enabled` | `true` | AT_LEAST_ONCE delivery service |
| `casehub.qhorus.delivery.batch-size` | `100` | Messages per delivery batch |
| `casehub.qhorus.delivery.max-consecutive-failures` | `10` | Circuit breaker threshold |
| `casehub.qhorus.delivery.reconciliation-interval` | `30s` | Stalled delivery retry interval |
| `casehub.qhorus.presence.away-timeout` | `PT2M` | Heartbeat absence before AWAY |
| `casehub.qhorus.presence.offline-timeout` | `PT10M` | Heartbeat absence before OFFLINE |
| `casehub.qhorus.presence.heartbeat-interval` | `PT30S` | Suggested client heartbeat interval |
| `casehub.qhorus.summary.enabled` | `true` | Channel summary auto-update scheduler |
| `casehub.qhorus.summary.check-interval-seconds` | `60` | Summary update sweep interval |
| `casehub.qhorus.protocol.lookback-size` | `50` | Protocol enforcement lookback window |
| `casehub.qhorus.protocol.request-response.max-open-queries` | `3` | Max open QUERYs before advisory |
| `casehub.qhorus.protocol.task-completion.max-open-commands` | `3` | Max open COMMANDs before advisory |
| `casehub.qhorus.protocol.contribution-required.max-consecutive` | `2` | Max consecutive messages from one sender |
| `casehub.qhorus.cleanup.stale-instance-seconds` | `120` | Instance staleness threshold |
| `casehub.qhorus.cleanup.data-retention-days` | `7` | Retention for messages and shared data |
| `casehub.qhorus.attestation.done-confidence` | `0.7` | SOUND attestation confidence for DONE |
| `casehub.qhorus.attestation.failure-confidence` | `0.6` | FLAGGED attestation confidence for FAILURE |
| `casehub.qhorus.attestation.decline-confidence` | `0.4` | FLAGGED attestation confidence for DECLINE |
| `casehub.qhorus.attestation.response-confidence` | `0.3` | FLAGGED attestation confidence for RESPONSE (wrong vocabulary) |
| `casehub.qhorus.attestation.peer-endorsed-confidence` | `0.4` | Peer ENDORSED attestation confidence |
| `casehub.qhorus.attestation.peer-challenged-confidence` | `0.5` | Peer CHALLENGED attestation confidence |
| `casehub.qhorus.agent-card.name` | `"Qhorus Agent Mesh"` | Agent card display name |
| `casehub.qhorus.agent-card.description` | (default text) | Agent card description |
| `casehub.qhorus.agent-card.url` | absent | Public URL of deployment |
| `casehub.qhorus.agent-card.version` | `"1.0.0"` | Agent card version |
| `casehub.qhorus.connector-backend.delivery-channel` | absent | Channel for delivery audit notifications |
| `casehub.qhorus.tracing.*` | all `true` | OpenTelemetry per-operation span flags |

### Normative Layer

Qhorus implements a 4-layer normative accountability framework:
1. **Illocutionary** -- what was said (speech act type, channel)
2. **Commitment** -- what was obligated (Commitment record, OPEN to FULFILLED/FAILED/EXPIRED)
3. **Temporal** -- when obligations become stale (Watchdog, deadline enforcement)
4. **Enforcement** -- casehub-engine orchestration reacts to commitment outcomes via CDI events

See [docs/normative-layer.md](https://raw.githubusercontent.com/casehubio/qhorus/main/docs/normative-layer.md).

### Ledger Integration

Every message of every type creates a tamper-evident ledger entry extending `casehub-ledger`. The ledger is the complete, immutable channel history. `MessageLedgerEntry` is a JOINED subclass of `JpaLedgerEntry` with Merkle hash chain and sequence allocation. Telemetry data from EVENT messages is extracted and indexed for aggregation queries.

Commitment outcomes (DONE, FAILURE, DECLINE, RESPONSE) trigger attestation writes on the originating COMMAND's ledger entry via `CommitmentAttestationPolicy`.

### Peer Attestation

Multi-agent verification on ledger entries. Peer agents can endorse or challenge attestation outcomes. `PeerReviewRequestedEvent` fires to solicit peer review. `PeerReviewAutoTrigger` and `PeerReviewResponseHandler` manage the peer review lifecycle.

---

## Consumer SPIs -- Extension Points

These interfaces let consumers customize Qhorus behaviour without modifying internals. Override with `@Alternative @Priority(1)`.

| SPI | Package | Purpose |
|-----|---------|---------|
| `CommitmentAttestationPolicy` | `api/spi/` | Determines attestation verdict and confidence when a message discharges a commitment |
| `ObligorTrustPolicy` | `api/spi/` | Trust gate for COMMAND dispatch -- permits or rejects based on obligor trust score |
| `InstanceActorIdProvider` | `api/spi/` | Maps session-scoped `instanceId` to persona-scoped ledger `actorId` |
| `SummaryUpdateHook` | `api/spi/` | Generates channel summaries (e.g. via LLM); receives `SummaryUpdateContext` |
| `ChannelProjection<S>` | `api/spi/` | Left-fold over message history for derived read-models |
| `RenderableProjection<S>` | `api/spi/` | Named projection with `render()` for MCP tool integration |
| `ChannelProtocol` | `api/spi/` | Pluggable message sequence validation (advisory enforcement) |
| `WatchdogAlertRouter` | `api/watchdog/` | Routes watchdog alerts to delivery targets |

---

## What This Repo Does NOT Do

- **Orchestrate agent workflows** -- that is casehub-engine
- **Manage human task inboxes** -- that is casehub-work
- **Own case state or process logic** -- Qhorus is purely infrastructure
- **Interpret message content** -- routes and records, never reasons about content
- **Provision or terminate AI agent processes** -- that is claudony
