# casehub-qhorus -- Consumer Guide

> Agent communication mesh and governance methodology for multi-agent AI systems.

**GitHub:** [casehubio/qhorus](https://github.com/casehubio/qhorus)
**Tier:** Foundation

---

## Purpose

Qhorus gives every agent interaction the formal status of an accountable act -- grounded in speech act theory, deontic logic, defeasible reasoning, and social commitment semantics. The LLM reasons; Qhorus enforces, records, and derives.

Any Quarkus app adds `io.casehub:casehub-qhorus` as a dependency and its agents immediately get typed channels, typed messages, shared data, an instance registry, normative audit ledger, and a channel gateway with backend-agnostic fan-out.

---

## Modules to Depend On

| Module | ArtifactId | When to use |
|--------|-----------|-------------|
| `api` | `casehub-qhorus-api` | Always -- SPIs, domain records, service facades |
| `runtime` | `casehub-qhorus` | Always -- core services, MCP tools, ledger integration |
| `persistence-memory` | `casehub-qhorus-persistence-memory` | In-memory stores for tests and zero-config ephemeral installs |
| `testing` | `casehub-qhorus-testing` | Test utilities (`RecordingChannelBackend`, `MessageLedgerEntryTestFactory`) |
| `connector-backend` | `casehub-qhorus-connector-backend` | Optional -- bridges casehub-connectors into Qhorus channels |
| `slack-channel` | `casehub-qhorus-slack-channel` | Optional -- Slack channel backend |
| `kafka-observer` | `casehub-qhorus-kafka-observer` | Optional -- Kafka message observer |
| `websocket-observer` | `casehub-qhorus-websocket-observer` | Optional -- WebSocket real-time push |
| `webhook-observer` | `casehub-qhorus-webhook-observer` | Optional -- HTTP POST webhook callbacks |
| `notification-bridge` | `casehub-qhorus-notification-bridge` | Optional -- commitment lifecycle to platform subscription engine |
| `postgres-broadcaster` | `casehub-qhorus-postgres-broadcaster` | Optional -- cross-node delivery via PostgreSQL LISTEN/NOTIFY |

Optional modules activate by classpath presence -- no configuration needed beyond adding the dependency.

---

## Key Abstractions

### Channels

Typed communication channels with configurable delivery semantics and access control.

| Semantic | Behaviour |
|----------|-----------|
| `APPEND` | Ordered message log (default) |
| `COLLECT` | Accumulates contributions |
| `BARRIER` | Blocks until all contributors respond |
| `EPHEMERAL` | Messages not persisted after delivery |
| `LAST_WRITE` | Latest value wins (version-aware, AT_LEAST_ONCE delivery) |

Channels support `allowedTypes` / `deniedTypes` for message type filtering, `allowedWriters` for ACL, `adminInstances` for administrative access, rate limiting, and protocol enforcement.

**Spaces** provide recursive channel hierarchy (max depth 10) for grouping related channels with parent/child nesting.

### Message Types (Speech-Act Taxonomy -- ADR-0005)

| Type | Intent | Creates obligation | Terminal? |
|------|--------|--------------------|-----------|
| QUERY | Information request | Yes -- RESPONSE or DECLINE required | No |
| COMMAND | Action request | Yes -- DONE, FAILURE, or DECLINE required | No |
| RESPONSE | Answers a QUERY | No (discharges QUERY obligation) | Yes (for that QUERY) |
| STATUS | Progress update on open COMMAND | No | No |
| DECLINE | Refuse a QUERY or COMMAND | No (discharges obligation) | Yes |
| HANDOFF | Delegate COMMAND to another agent | Transfers obligation | No |
| DONE | Successful COMMAND completion | No | Yes |
| FAILURE | Failed COMMAND | No | Yes |
| EVENT | Telemetry / observer signal | No | N/A -- excluded from agent context |

Builder invariants (enforced at `build()`):
- RESPONSE, DONE, FAILURE, DECLINE, HANDOFF require `inReplyTo` + `correlationId`
- HANDOFF requires `target` (named recipient or capability tag)

### Commitment Lifecycle

Obligations created by QUERY and COMMAND messages follow a state machine:

```
OPEN --> ACKNOWLEDGED --> FULFILLED
                     --> FAILED
                     --> DECLINED
                     --> DELEGATED (via HANDOFF)
                     --> EXPIRED (deadline enforcement)
```

CDI events fired on state transitions:
- `CommitmentDeclinedEvent` -- when a commitment transitions to DECLINED
- `CommitmentExpiredEvent` -- when deadline-based expiry fires

### Dispatch Gate

All channel writes flow through a single enforcement gate: `MessageService.dispatch(MessageDispatch)`.

Pipeline (in order): paused check, `AllowedWritersPolicy` ACL, `RateLimiter`, `ObligorTrustPolicy` SPI, `MessageTypePolicy`, `CorrelationIntegrityChecker` (advisory), `ProtocolEvaluation` (advisory), LAST_WRITE overwrite semantics, `LedgerWriteService.record()`, `ChannelGateway.fanOut()`.

There is no bypass path.

`MessageDispatch` builds via `MessageDispatch.builder(channelId, sender, type, content)`. `DispatchResult` carries `ledgerEntryId`, `subjectId`, `causedByEntryId`, `parentReplyCount`, and `advisories`.

### Service Facades (Consumer-Facing)

Consumers interact through typed service facade interfaces in `api/`:

| Interface | Location | Purpose |
|-----------|----------|---------|
| `ChannelManager` | `api/channel/` | Blocking channel operations (create, find, modify) |
| `ReactiveChannelManager` | `api/channel/` | Reactive channel operations (`Uni<T>`) |
| `MessageDispatcher` | `api/message/` | Blocking message dispatch |
| `ReactiveMessageDispatcher` | `api/message/` | Reactive message dispatch (`Uni<T>`) |

### Channel Projection SPI

Left-fold SPI over channel message history. Implement `ChannelProjection<S>` to derive deterministic read-models (vote tallies, review manifests, digests) without scanning raw messages on every read.

- `identity()` returns fresh empty state
- `apply(S, MessageView)` folds one message
- `ProjectionResult<S>` supports incremental projection (pass as `previous` to resume without full rescan)

Topic-aware projections: `project_channel` MCP tool accepts optional `topic` and `max_messages` parameters.

### Store SPIs

Nine store interfaces (blocking and reactive mirrors) in `api/store/` cover channels, messages, instances, shared data, watchdogs, commitments, spaces, topics, and channel memberships. JPA implementations in `runtime/` use `*Entity` suffixed classes.

### Channel Gateway

Outbound messages route through a channel backend SPI supporting multiple backend types:
- **Agent-to-agent** (default `QhorusChannelBackend`)
- **Human-participating** (`HumanParticipatingChannelBackend`)
- **Human-observer** (`HumanObserverChannelBackend`)

`MessageObserver` SPI: `onMessage(MessageReceivedEvent)` with `Scope.LOCAL` (in-JVM) or `Scope.CLUSTER` (cross-node). Channel name filtering via `channels()` method.

`ChannelInitialisedEvent` fires on channel creation and startup recovery -- external backends observe this to re-register without implementing their own restart logic.

### MCP Tool Surface

Qhorus exposes MCP tools scoped to `@McpServer("qhorus")` across capability groups: instance management, channel management, backend management, messaging, shared data, commitments, normative ledger queries, spaces, topics, presence, and membership.

Build-property switching: `QhorusMcpTools` activates when `casehub.qhorus.reactive.enabled` is false/absent; `ReactiveQhorusMcpTools` activates when true.

### External APIs

- `GET /.well-known/agent-card.json` -- A2A ecosystem discovery
- `POST /a2a/message:send` -- A2A-compatible message receive endpoint
- `GET /a2a/tasks/{id}/stream` -- SSE streaming with keepalive events

---

## Configuration

### Named Datasource Requirement

Qhorus always runs on a named `qhorus` datasource. Never share it with domain tables.

```properties
quarkus.datasource.qhorus.db-kind=postgresql
quarkus.datasource.qhorus.jdbc.url=jdbc:postgresql://localhost:5432/qhorus
```

### Reactive vs Blocking

Set `casehub.qhorus.reactive.enabled=true` (BUILD_TIME) to activate the reactive stack. Default is blocking.

### Key Configuration Properties

| Property | Default | Purpose |
|----------|---------|---------|
| `casehub.qhorus.reactive.enabled` | `false` | BUILD_TIME: activate reactive stack |
| `casehub.qhorus.commitment.min-obligor-trust` | `0` | Trust threshold for COMMAND dispatch |
| `casehub.qhorus.commitment.default-query-deadline` | absent | Default deadline for QUERYs without explicit deadline |
| `casehub.qhorus.a2a.sse.heartbeat-interval-seconds` | `15` | SSE keepalive interval |
| `casehub.qhorus.a2a.sse.max-duration-seconds` | `1800` | SSE max stream duration |
| `casehub.qhorus.delivery.*` | -- | AT_LEAST_ONCE delivery config (enabled, batchSize, maxConsecutiveFailures) |
| `casehub.qhorus.tracing.*` | all `true` | OpenTelemetry per-operation span flags |
| `casehub.qhorus.presence.*` | -- | Presence timeouts and heartbeat interval |
| `casehub.qhorus.protocol.lookback-size` | `50` | Protocol enforcement lookback window |

### Normative Layer

Qhorus implements a 4-layer normative accountability framework:
1. **Illocutionary** -- what was said (speech act type, channel)
2. **Commitment** -- what was obligated (Commitment record, OPEN to FULFILLED/FAILED/EXPIRED)
3. **Temporal** -- when obligations become stale (Watchdog, deadline enforcement)
4. **Enforcement** -- casehub-engine orchestration reacts to commitment outcomes via CDI events

See [docs/normative-layer.md](https://raw.githubusercontent.com/casehubio/qhorus/main/docs/normative-layer.md).

### Ledger Integration

Every message of every type creates a tamper-evident ledger entry extending `casehub-ledger`. The ledger is the complete, immutable channel history. Telemetry data from EVENT messages is extracted and indexed for aggregation queries.

---

## What This Repo Does NOT Do

- **Orchestrate agent workflows** -- that is casehub-engine
- **Manage human task inboxes** -- that is casehub-work
- **Own case state or process logic** -- Qhorus is purely infrastructure
- **Interpret message content** -- routes and records, never reasons about content
- **Provision or terminate AI agent processes** -- that is claudony
