# casehub-qhorus -- Contributor Guide

> Agent communication mesh and governance methodology for multi-agent AI systems.

**GitHub:** [casehubio/qhorus](https://github.com/casehubio/qhorus)

---

## Module Structure

| Module | ArtifactId | Contents |
|--------|-----------|----------|
| `api` | `casehub-qhorus-api` | SPIs, domain records, service facades, reader interfaces, gateway contracts, store interfaces, watchdog types, query types. No JPA -- pure Java records and interfaces. |
| `runtime` | `casehub-qhorus` | Extension runtime -- JPA entities, services, MCP tools (`@McpServer("qhorus")`), REST API (`ChannelResource`), A2A endpoint, ledger integration, OpenTelemetry tracing, delivery service, watchdog evaluation, channel summary scheduler, protocol enforcement engine. |
| `deployment` | `casehub-qhorus-deployment` | Quarkus extension deployment descriptors; `QhorusProcessor` for build-time feature registration and native config. |
| `connector-backend` | `casehub-qhorus-connector-backend` | Optional -- `ConnectorChannelBackend` implements `HumanParticipatingChannelBackend`; bridges `InboundMessage` CDI events from casehub-connectors into Qhorus channel dispatch; `ConnectorQhorusMeshBridge` posts STATUS after delivery; activates by classpath. |
| `connectors` | `casehub-qhorus-connectors` | Optional -- `ConnectorAlertBridge` routes `WatchdogAlertEvent` to casehub-connectors via `ConnectorService`; formats alert bodies from the sealed `AlertContext` hierarchy; activates by classpath. |
| `slack-channel` | `casehub-qhorus-slack-channel` | Optional -- `SlackChannelBackend` implements `HumanParticipatingChannelBackend`; thread continuity via composite cache; REST bindings; activates by classpath. |
| `persistence-memory` | `casehub-qhorus-persistence-memory` | Standalone in-memory store implementations for all store SPIs; used by `@QuarkusTest` in consumers. |
| `testing` | `casehub-qhorus-testing` | Test utilities (`RecordingChannelBackend`, `MessageLedgerEntryTestFactory`); depends on `persistence-memory` transitively. |
| `kafka-observer` | `casehub-qhorus-kafka-observer` | Optional -- `KafkaMessageObserver` (scope: `LOCAL`); serialises to CloudEvent via `CloudEventMapper`; activates by classpath. |
| `websocket-observer` | `casehub-qhorus-websocket-observer` | Optional -- `WebSocketMessageObserver` (scope: `CLUSTER`); push via `WebSocketConnectionRegistry`; catch-up replay via `lastEventId`; activates by classpath. |
| `webhook-observer` | `casehub-qhorus-webhook-observer` | Optional -- `WebhookMessageObserver` (scope: `CLUSTER`); JPA-persisted registrations; HMAC-SHA256 signing; `CredentialResolver` SPI; activates by classpath. |
| `notification-bridge` | `casehub-qhorus-notification-bridge` | Optional -- commitment lifecycle to platform subscription engine via `SubscribableEvent`; `QhorusObligationEvent` with Kind enum. |
| `postgres-broadcaster` | `casehub-qhorus-postgres-broadcaster` | Optional -- `PostgresChannelActivityBroadcaster` for cross-node delivery via PostgreSQL LISTEN/NOTIFY; exponential backoff reconnection. |
| `examples` | (multi-module) | `@QuarkusTest` integration examples: `type-system/` (CI, no model), `normative-layout/` (CI, no model), `agent-communication/` (requires `-Pwith-llm-examples` + Jlama). |

### API Interface Taxonomy

Five categories across `api/`:

- **`api/store/`** -- Data access (CRUD): blocking store interfaces with reader sub-interfaces; cross-tenant variants for administrative queries; structured query types in `api/store/query/`.
- **`api/spi/`** -- Extension points: consumers replace policies, attestation, identity, projections, summaries, and protocols with custom beans via `@Alternative @Priority`.
- **`api/gateway/`** -- Integration contracts: channel backend hierarchy (`AgentChannelBackend`, `HumanParticipatingChannelBackend`, `HumanObserverChannelBackend`), `MessageObserver`, `ChannelActivityBroadcaster`, inbound normalisation (`InboundNormaliser`, `NormalisedMessage`, `InboundHumanMessage`), outbound messaging (`OutboundMessage`), delivery (`DeliveryGuarantee`, `DeliveryCursor`), lifecycle events (`ChannelInitialisedEvent`, `ChannelClosedEvent`, `CommitmentStateChangedEvent`).
- **`api/channel/`, `api/message/`** -- Service facades and domain records: mutating facades (`ChannelManager`, `MessageDispatcher`, `ConsumerMessaging`, `TopicManager`, `MembershipManager`, `PresenceTracker`, `ReactionManager`) and reader interfaces (`ChannelReader`); domain records (`Channel`, `Message`, `MessageView`, `Commitment`, `ChannelSummary`, `Topic`, `Reaction`, etc.).
- **`api/watchdog/`** -- Watchdog domain types: `WatchdogConditionType` (11 conditions), `Watchdog` record, `WatchdogAlertEvent`, `WatchdogAlertRouter` SPI, sealed `AlertContext` hierarchy with typed context records for each condition.

---

## Internal Architecture

### Dispatch Gate

All channel writes flow through `MessageService.dispatch(MessageDispatch)`. Pipeline (in order):

1. **Paused check** -- `channel.paused()` rejects all writes
2. **`AllowedWritersPolicy`** ACL -- checks `channel.allowedWriters()`
3. **`RateLimiter`** -- per-channel and per-instance limits via in-memory counters
4. **`ObligorTrustPolicy` SPI** -- invoked for COMMAND messages with named targets; bypassed for role/capability-prefixed targets
5. **`MessageTypePolicy`** (via `StoredMessageTypePolicy`) -- enforces `allowedTypes`/`deniedTypes` from channel config
6. **`CorrelationIntegrityChecker`** (advisory) -- validates `inReplyTo` references exist and `correlationId` matches; adds advisories to `DispatchResult`
7. **`ProtocolEvaluation`** (advisory via `ProtocolRegistry`) -- evaluates channel protocols against recent message history
8. **LAST_WRITE overwrite** -- for `LAST_WRITE` channels, replaces the previous value (version-aware)
9. **`LedgerWriteService.record()`** -- creates `MessageLedgerEntry` with Merkle hash chain
10. **`ChannelGateway.fanOut()`** -- delivers to backends and fires `MessageReceivedEvent` via observers

There is no bypass path.

### Channel Gateway Internals

`ChannelGateway` manages backend registration and fan-out. Key behaviours:

- **Startup recovery:** rebuilds the in-memory registry from `ChannelService.listAll()` on `@Observes StartupEvent` (exception-isolated per channel). `ChannelInitialisedEvent` fires on every `initChannel()`.
- **Fan-out:** non-default backends receive messages asynchronously and non-fatally. The default `QhorusChannelBackend` is always registered.
- **Observer dispatch:** `MessageObserverDispatcher` (package-private static utility) iterates all `MessageObserver` beans via `Instance<MessageObserver>`, applies `channels()` filter and `scope()` check, nulls EVENT content, and isolates per-observer failures with try-catch. `Handle.close()` in finally block ensures `@Dependent`-scoped observers are destroyed.
- **Inbound normalisation:** `ChannelGateway.receiveHumanMessage()` delegates to `InboundNormaliser` (per-channel via `HumanParticipatingChannelBackend.normaliserFor()` or system `DefaultInboundNormaliser`).
- **Delivery service integration:** `DeliverySignalQueue` mediates between `MessageService` and `DeliveryService` for AT_LEAST_ONCE backends.

### Channel Creation

`ChannelCreateHelper` is the single source of truth for channel creation -- package-private `@ApplicationScoped` with `@Transactional(REQUIRES_NEW)`. `ChannelService.create()`, `findOrCreateByName()`, and `findOrCreateWithBinding()` all delegate to it. The REQUIRES_NEW boundary isolates creation failures from the caller's transaction for race recovery on PostgreSQL.

`ChannelCreateRequest` validates at construction: slug validation, connector binding all-or-nothing check, `allowedTypes`/`deniedTypes` overlap rejection.

### Ledger Integration

`LedgerWriteService.record()` writes `MessageLedgerEntry` (JOINED subclass of `JpaLedgerEntry`) for all 9 message types in a `REQUIRES_NEW` transaction. Key internals:

- **Sequence allocation:** `QhorusSequenceAllocator` with `REQUIRES_NEW` for isolation
- **Actor ID tokenisation:** `InstanceActorIdProvider` SPI resolves instanceId to actorId
- **Merkle hash chain:** `QhorusLedgerMerkleFrontierRepository` maintains the frontier
- **subjectId / causedByEntryId propagation priority:** explicit caller value, then correlation root lookup (earliest by `sequenceNumber ASC`), then `channelId` fallback
- **Attestation:** For terminal commitment outcomes (DONE, FAILURE, DECLINE, RESPONSE), delegates to `CommitmentAttestationPolicy.attestationFor()` with `CommitmentContext`

### Peer Attestation

`PeerAttestationWriter` writes endorsement/challenge attestations. `PeerReviewAutoTrigger` fires `PeerReviewRequestedEvent` on commitment terminal outcomes. `PeerReviewResponseHandler` processes incoming peer review responses. `ReviewerResolver` determines which instances should review based on channel `reviewerInstances`.

### Protocol Enforcement SPI

`ChannelProtocol` interface in `api/spi/` -- pluggable message sequence validation:

```java
public interface ChannelProtocol {
    String protocolName();
    List<String> evaluate(ProtocolContext context);
}
```

`ProtocolRegistry` discovers implementations via CDI. `ProtocolContext` provides channel state, recent messages, and protocol participants. All enforcement is advisory -- violations add to `DispatchResult.advisories()`.

Built-in protocols:

| Protocol | Class | What it enforces |
|----------|-------|-----------------|
| `REQUEST_RESPONSE` | `RequestResponseProtocol` | Max open QUERYs per channel (configurable, default 3) |
| `TASK_COMPLETION` | `TaskCompletionProtocol` | Max open COMMANDs per channel (configurable, default 3) |
| `ROUND_ROBIN` | `RoundRobinProtocol` | Fair turn-taking among participants |
| `CONTRIBUTION_REQUIRED` | `ContributionRequiredProtocol` | Max consecutive messages from one sender (configurable, default 2) |

### Delivery Service

`DeliveryService` -- event-driven delivery pump for AT_LEAST_ONCE backends:

- **Signal-driven:** `DeliverySignalQueue` receives signals from `MessageService` after dispatch
- **Batch execution:** `DeliveryBatchExecutor` handles `@Transactional` cursor-per-batch advancement
- **Reconciliation:** `@Scheduled reconcileAll()` backup (configurable, default 30s) catches missed signals
- **Health circuit breaker:** `consecutiveFailures` counter; backends marked as degraded after threshold
- **Opt-in tracking:** channels with `trackDelivery=true` get per-participant delivery cursors for BARRIER and COLLECT channels

### Channel Summary System

`ChannelSummaryService` manages per-channel summaries. `ChannelSummaryScheduler` runs periodic sweeps to detect channels needing summary updates (based on `updateAfterMessages` and `updateAfterSeconds` thresholds).

Summary generation delegates to `SummaryUpdateHook` SPI. The runtime ships `NoOpSummaryUpdateHook` as `@DefaultBean`. Consumers provide LLM-backed implementations via `@Alternative @Priority(1)`.

`SummaryResult(text, annotations)` supports arbitrary key-value annotations that round-trip through storage. `SummaryUpdateContext` provides `recentMessages`, `messageQuery` function for ad-hoc queries, and `messagesSinceLastUpdate` count.

`ChannelSummaryUpdatedEvent` fires on every summary update for downstream observers.

### Watchdog Evaluation

`WatchdogEvaluationService` evaluates all enabled watchdog conditions on a scheduled interval. Each condition type has a typed `AlertContext` record (sealed hierarchy) in `api/watchdog/`:

- `BarrierStuckContext(channelName, missingContributors, elapsedSeconds)`
- `ApprovalPendingContext(pendingCount, oldestExpiryAt)`
- `AgentStaleContext(staleCount, staleInstanceIds)`
- `ChannelIdleContext(channelNames, thresholdSeconds)`
- `QueueDepthContext(channelName, messageCount, threshold)`
- `ContextPressureContext(channelName, actorId, contextWindowPct)`
- `LoopDetectedContext(channelName, sender, messageCount, maxSimilarity)`
- `ObligationFanOutContext(channelName, staleCount, correlationIds)`
- `ConversationStallContext(channelName, stalledCount, correlationIds, stalledSeconds)`
- `EchoChamberContext(channelName, participants, maxSimilarity)`
- `CircularDelegationContext(channelName, correlationId, cycle, chainDepth)`

`JaccardSimilarity` (package-private utility) provides similarity scoring for `LOOP_DETECTED` and `ECHO_CHAMBER` conditions.

Alert routing: `WatchdogAlertRouter` SPI (default `ConfiguredWatchdogAlertRouter` reads from `casehub.qhorus.watchdog.alert.endpoints`). `ConnectorAlertBridge` in the `connectors` module dispatches formatted alerts via `ConnectorService`.

### REST API Internals

`ChannelResource` at `/api/channels` -- `@ApplicationScoped` JAX-RS resource:

- Channel resolution supports both UUID and name lookups via `tryParseUuid()` fallback
- `ChannelResponse.from()` enriches with message count and space name
- List endpoint supports `ChannelQuery`-based filtering (prefix, spaceId, paused)
- All setting mutations go through `ChannelService` methods
- Error responses use `ErrorResponse(error)` record

### Principal Integration

`QhorusInboundCurrentPrincipal` -- `@ApplicationScoped @Alternative @Priority(1)` reads `X-Tenancy-ID` header via `TenancyContextFilter` (`@PreMatching @Priority(100)`) and populates `CurrentPrincipal.tenancyId()`. Displaced by `OidcCurrentPrincipal @Priority(100)` from `casehub-platform-oidc`.

`TenancyContextFilter` populates `InboundTenancyContext` (`@RequestScoped`) for ALL HTTP requests. `QhorusInboundCurrentPrincipal` catches `ContextNotActiveException` for background-thread safety (returns `DEFAULT_TENANT_ID`).

Test note: modules including both qhorus runtime and casehub-platform must add `quarkus.arc.exclude-types=io.casehub.platform.mock.MockCurrentPrincipal` in test properties.

### Evidential Checker

`EvidentialChecker` (`@DefaultBean @ApplicationScoped`) -- two entry points:
- `check(String messageType, String content, BenchmarkContext)` -- benchmark path (Zone 1-3 variant checks)
- `checkObligation(String terminalType, CommitmentContext)` -- attestation path vocabulary check

`BenchmarkContext` carries variant-specific ground truth. `BenchmarkViolation` is the output record. Injectable by consumers (e.g. casehub-devtown pre-attestation checks).

### OpenTelemetry Tracing

`QhorusTracingConfig` provides per-operation span flags (all `true` by default). Traces are instrumented across dispatch, commitment lifecycle, gateway fan-out, and ledger writes.

### Entity Mapping

`QhorusEntityMapper` (`@ApplicationScoped`) provides centralized JPA entity to API record conversion for all domain types: channels, messages, instances, shared data, commitments, watchdogs, spaces, topics, memberships, reactions, summaries.

### Store Implementation Layer

All JPA store implementations live in `runtime/store/jpa/` following a `Jpa*Store` naming convention:

| Implementation | Store Interface | Notes |
|---------------|----------------|-------|
| `JpaChannelStore` | `ChannelStore` | Full CRUD, query support |
| `JpaChannelBindingStore` | `ChannelBindingStore` | Connector binding CRUD |
| `JpaChannelMembershipStore` | `ChannelMembershipStore` | Membership with auto-join |
| `JpaChannelSummaryStore` | `ChannelSummaryStore` | Summary persistence |
| `JpaCommitmentStore` | `CommitmentStore` | Commitment lifecycle |
| `JpaMessageStore` | `MessageStore` | Message persistence; `MessageQueryJpql` builds dynamic JPQL |
| `JpaInstanceStore` | `InstanceStore` | Agent registry |
| `JpaDataStore` | `DataStore` | Key-value with artefact claims |
| `JpaWatchdogStore` | `WatchdogStore` | Watchdog condition persistence |
| `JpaSpaceStore` | `SpaceStore` | Space hierarchy |
| `JpaTopicStore` | `TopicStore` | Topic persistence |
| `JpaReactionStore` | `ReactionStore` | Reaction persistence |
| `JpaDeliveryCursorStore` | `DeliveryCursorStore` | Delivery cursor tracking |
| `CommitmentPanacheRepo` | -- | Panache repository for `CommitmentEntity` |
| `ChannelSummaryPanacheRepo` | -- | Panache repository for `ChannelSummaryEntity` |
| `DeliveryCursorPanacheRepo` | -- | Panache repository for `DeliveryCursorEntity` |

Cross-tenant stores (`JpaCrossTenant*Store`) bypass tenancy filtering for administrative operations.

---

## Dependencies

### Depends On

| Dependency | Relationship |
|-----------|-------------|
| `casehub-ledger` | Mandatory -- ledger entry subclassing (`MessageLedgerEntry` extends `JpaLedgerEntry`), attestation, Merkle hash chain, sequence allocation |
| `casehub-platform-api` | Direct compile -- `ActorType`, `ActorTypeResolver` from `io.casehub.platform.api.identity` |

### Depended On By

| Repo | How |
|------|-----|
| `claudony` | Embeds Qhorus directly; named `qhorus` datasource; provides CaseChannel SPI implementation |
| `casehub-engine` | Future -- via CaseChannelProvider SPI (implemented by Claudony) |
| `casehub-blocks` | Channel projections, summary integration, convergence detection |

---

## Current State

- 1035+ tests passing (runtime + testing + examples modules)
- Reactive tier fully retired -- virtual threads on Quarkus 3.32+ replace reactive stack
- Channel backend abstraction complete (agent, human-participating, human-observer modes) -- ADR-0006
- A2A protocol bridge complete: backend, identity resolution chain, and resource layer
- Dispatch unification complete: `MessageService.dispatch(MessageDispatch)` is the single enforcement gate
- `DispatchResult` carries `messageId`, `ledgerEntryId`, `subjectId`, `causedByEntryId`, `parentReplyCount`, `advisories`
- REST API for channel CRUD at `/api/channels` -- #387
- Channel protocol enforcement SPI with 4 built-in protocols -- #357
- Peer attestation with multi-agent verification -- #356
- Channel summary slot with `SummaryUpdateHook` SPI -- #355
- 11 watchdog condition types including coordination pathology detection -- #354, #363, #368
- Opt-in per-participant delivery tracking for BARRIER and COLLECT channels -- #376
- Notification bridge migrated to platform subscription engine -- #375
- Consumer API facades and reader interfaces extracted to `api/` -- #22
- `target` and `actorType` added to `MessageReceivedEvent` -- observer routing enrichment
- Actor type explicitly stored on every message and propagated to ledger without re-derivation
- `SummaryUpdateHook` enriched with `SummaryResult` (text + annotations) and `SummaryUpdateContext` (recentMessages, messageQuery) -- #359

---

## Design Documents

- [docs/DESIGN.md](https://raw.githubusercontent.com/casehubio/qhorus/main/docs/DESIGN.md) -- full MCP tool surface, store SPIs, commitment lifecycle, domain model, service catalogue
- [docs/normative-layer.md](https://raw.githubusercontent.com/casehubio/qhorus/main/docs/normative-layer.md) -- 4-layer normative accountability framework
- [docs/normative-framework.md](https://raw.githubusercontent.com/casehubio/qhorus/main/docs/normative-framework.md) -- normative framework details
- [docs/agent-protocol-comparison.md](https://raw.githubusercontent.com/casehubio/qhorus/main/docs/agent-protocol-comparison.md) -- Qhorus vs A2A and ACP
- [docs/multi-agent-framework-comparison.md](https://raw.githubusercontent.com/casehubio/qhorus/main/docs/multi-agent-framework-comparison.md) -- full multi-agent framework comparison
- [adr/INDEX.md](https://raw.githubusercontent.com/casehubio/qhorus/main/adr/INDEX.md) -- 17 architectural decision records (incl. ADR-0005 speech-act taxonomy, ADR-0006 channel backend abstraction, ADR-0009 dispatch builder audit chain)
