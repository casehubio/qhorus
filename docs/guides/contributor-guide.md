# casehub-qhorus -- Contributor Guide

> Agent communication mesh and governance methodology for multi-agent AI systems.

**GitHub:** [casehubio/qhorus](https://github.com/casehubio/qhorus)

---

## Module Structure

| Module | Contents |
|--------|----------|
| `api` | SPIs: `ChannelBackend`, `MessageObserver` (with `Scope.LOCAL`/`CLUSTER` and `channels()` filter), `HumanParticipatingChannelBackend`, `ChannelProjection<S>`, `CommitmentAttestationPolicy`; Store SPIs in `api/store/` (blocking + reactive mirrors, cross-tenant variants, query types); Domain records in `api/{channel,message,instance,data,watchdog}/`; Service facades (`ChannelManager`, `ReactiveChannelManager`, `MessageDispatcher`, `ReactiveMessageDispatcher`); DTOs: `MessageView`, `ProjectionResult<S>`, `CommitmentContext` |
| `runtime` | `MessageService`, `ChannelGateway`, `ProjectionService`, `ReactiveProjectionService`, `SpaceService`, `TopicService`, `PresenceService`, `ChannelMembershipService`, ledger integration, MCP tools (`@McpServer("qhorus")`), A2A endpoint, `QhorusCloudEventAdapter`, OpenTelemetry tracing (`QhorusTracingConfig`), `EvidentialChecker` |
| `deployment` | Quarkus extension deployment descriptors; `QhorusBuildTimeConfig` declares `casehub.qhorus.reactive.enabled` |
| `connector-backend` | Optional -- `ConnectorChannelBackend` implements `HumanParticipatingChannelBackend`; bridges `InboundMessage` CDI events from casehub-connectors; `ConnectorQhorusMeshBridge` posts STATUS after delivery; activates by classpath |
| `slack-channel` | Optional -- `SlackChannelBackend` implements `HumanParticipatingChannelBackend`; thread continuity via composite cache; REST bindings; activates by classpath |
| `persistence-memory` | Standalone in-memory store implementations; ArtifactId: `casehub-qhorus-persistence-memory`; moved from `testing/` |
| `testing` | Test utilities (`RecordingChannelBackend`, `MessageLedgerEntryTestFactory`); depends on `persistence-memory` transitively |
| `kafka-observer` | Optional -- `KafkaMessageObserver` (scope: `LOCAL`); serialises to CloudEvent via `CloudEventMapper`; activates by classpath |
| `websocket-observer` | Optional -- `WebSocketMessageObserver` (scope: `CLUSTER`); push via `WebSocketConnectionRegistry`; catch-up replay via `lastEventId`; activates by classpath |
| `webhook-observer` | Optional -- `WebhookMessageObserver` (scope: `CLUSTER`); JPA-persisted registrations; HMAC-SHA256 signing; activates by classpath |
| `notification-bridge` | Optional -- commitment lifecycle to platform subscription engine via `SubscribableEvent`; `QhorusObligationEvent` with Kind enum |
| `postgres-broadcaster` | Optional -- `PostgresChannelActivityBroadcaster` for cross-node delivery via PostgreSQL LISTEN/NOTIFY; exponential backoff reconnection |
| `examples` | `@QuarkusTest` integration examples; `type-system/` (CI, no model), `normative-layout/` (CI, no model), `agent-communication/` (requires `-Pwith-llm-examples` + Jlama) |

### API Interface Taxonomy

Four categories across `api/`:
- **`api/store/`** -- Data access (CRUD): blocking + reactive pairs; consumers implement via JPA
- **`api/spi/`** -- Extension points: consumers replace policies/attestation/identity with custom beans
- **`api/gateway/`** -- Integration contracts: consumers implement backends (`AgentChannelBackend`, `HumanParticipatingChannelBackend`)
- **`api/channel/`, `api/message/`** -- Service facades: consumers call (`ChannelManager`, `MessageDispatcher`); read-only, no impl needed

---

## Internal Architecture

### Dispatch Gate

All channel writes flow through `MessageService.dispatch(MessageDispatch)`. Pipeline (in order): paused check, `AllowedWritersPolicy` ACL, `RateLimiter`, `ObligorTrustPolicy` SPI, `MessageTypePolicy`, `CorrelationIntegrityChecker` (advisory), `ProtocolEvaluation` (advisory via `ProtocolRegistry`), LAST_WRITE overwrite, `LedgerWriteService.record()`, `ChannelGateway.fanOut()`.

There is no bypass path. `ReactiveMessageService` mirrors this with `dispatch(MessageDispatch) -> Uni<DispatchResult>`.

### Channel Gateway Internals

`ChannelGateway` manages backend registration and fan-out. Startup recovery rebuilds the in-memory registry from `ChannelService.listAll()` on `@Observes StartupEvent` (exception-isolated per channel). `ChannelInitialisedEvent` fires on every `initChannel()` for backend re-registration.

Fan-out to non-default backends is asynchronous and non-fatal. The default `QhorusChannelBackend` is always registered. `MessageObserverDispatcher` iterates observers with per-observer try-catch, nulling EVENT content.

### Channel Creation

`ChannelCreateHelper` is the single source of truth for channel creation -- package-private `@ApplicationScoped` with `@Transactional(REQUIRES_NEW)`. `ChannelService.create()`, `findOrCreateByName()`, and `findOrCreateWithBinding()` all delegate to it. The REQUIRES_NEW boundary isolates creation failures from the caller's transaction for race recovery on PostgreSQL.

### Ledger Integration

`LedgerWriteService.record()` writes `MessageLedgerEntry` (JOINED subclass of `JpaLedgerEntry`) for all 9 message types. `QhorusLedgerEntryRepository` handles sequence allocation (via `QhorusSequenceAllocator` with `REQUIRES_NEW`), actor ID tokenisation, and Merkle hash chain.

`subjectId` / `causedByEntryId` propagation priority: explicit caller value, then correlation root lookup (earliest by `sequenceNumber ASC`), then `channelId` fallback.

### Protocol Enforcement SPI

`ChannelProtocol` interface in `api/spi/` -- pluggable message sequence validation. `ProtocolRegistry` discovers implementations via CDI (ProjectionRegistry pattern). Built-in protocols: `REQUEST_RESPONSE`, `TASK_COMPLETION`, `ROUND_ROBIN`, `CONTRIBUTION_REQUIRED`. All enforcement is advisory.

### Delivery Service

`DeliveryService` -- event-driven delivery pump for AT_LEAST_ONCE backends. `@Scheduled reconcileAll()` backup (30s). Health circuit breaker via `consecutiveFailures` and `unhealthy` set. `DeliveryBatchExecutor` handles `@Transactional` cursor-per-batch advancement. `DeliverySignalQueue` mediates between `MessageService` and `DeliveryService`.

### Principal Integration

`QhorusInboundCurrentPrincipal` -- `@ApplicationScoped` reads `X-Tenancy-ID` header via `TenancyContextFilter` (`@PreMatching @Priority(100)`) and populates `CurrentPrincipal.tenancyId()`. Displaced by any `@Alternative`.

Test note: modules including both qhorus runtime and casehub-platform must add `quarkus.arc.exclude-types=io.casehub.platform.mock.MockCurrentPrincipal` in test properties.

### Evidential Checker

`EvidentialChecker` (`@DefaultBean @ApplicationScoped`) -- two entry points:
- `check(String messageType, String content, BenchmarkContext)` -- benchmark path (Zone 1-3 variants)
- `checkObligation(String terminalType, CommitmentContext)` -- attestation path vocabulary check

Injectable by consumers (e.g. casehub-devtown pre-attestation checks).

---

## Dependencies

### Depends On

| Dependency | Relationship |
|-----------|-------------|
| `casehub-ledger` | Mandatory -- ledger entry subclassing and observability |
| `casehub-platform-api` | Direct compile -- `ActorType`, `ActorTypeResolver` from `io.casehub.platform.api.identity` |

### Depended On By

| Repo | How |
|------|-----|
| `claudony` | Embeds Qhorus directly; named `qhorus` datasource; provides CaseChannel SPI implementation |
| `casehub-engine` | Future -- via CaseChannelProvider SPI (implemented by Claudony) |

---

## Current State

- 1035+ tests passing (runtime + testing + examples modules)
- Channel backend abstraction complete (agent, human-participating, human-observer modes) -- ADR-0006
- A2A protocol bridge complete: backend, identity resolution chain, and resource layer
- Dispatch unification complete: `MessageService.send()` replaced by `dispatch(MessageDispatch)`; single enforcement gate
- `DispatchResult` carries `ledgerEntryId`, `subjectId`, `causedByEntryId`, `parentReplyCount`
- Deadline enforcement: `MessageDispatch.deadline` propagated to `Message.deadline` for all types
- `ReactiveMessageService.dispatch(MessageDispatch) -> Uni<DispatchResult>` replaces `send()`; full enforcement parity deferred
- Startup recovery via `@Observes StartupEvent` in `ChannelGateway`; `ChannelInitialisedEvent` fires on every `initChannel()`
- Actor type explicitly stored on every message and propagated to ledger without re-derivation
- Reactive store tests disabled -- require PostgreSQL with native reactive driver

---

## Design Documents

- [docs/DESIGN.md](https://raw.githubusercontent.com/casehubio/qhorus/main/docs/DESIGN.md) -- full MCP tool surface, store SPIs, commitment lifecycle
- [docs/normative-layer.md](https://raw.githubusercontent.com/casehubio/qhorus/main/docs/normative-layer.md) -- 4-layer normative accountability framework
- [adr/INDEX.md](https://raw.githubusercontent.com/casehubio/qhorus/main/adr/INDEX.md) -- architectural decision records (incl. ADR-0005 speech-act taxonomy)
