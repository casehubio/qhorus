# A2A Agent Interoperability — Design Spec

**Issue:** casehubio/qhorus#396
**Date:** 2026-08-19
**Depends on:** None (engine migration follow-on: casehubio/engine#940)

---

## Problem

Qhorus agents cannot communicate with agents built in other frameworks (embabel-agent, LangChain, CrewAI). The A2A protocol is the emerging interop standard, and qhorus has partial support — inbound A2A messages work via `A2AResource` + `A2AChannelBackend`, but:

1. **No outbound path through channels** — delegating work to an external A2A agent bypasses the mesh (engine's `A2AWorkerFunctionHandler` calls HTTP directly, invisible to ledger/commitments/observers)
2. **Protocol types are scattered** — inline records in `A2AResource`, duplicated `AgentCard` in engine, no shared model
3. **Agent card is static** — hardcoded infrastructure skills, no per-agent discovery
4. **Message model gap** — no structured data support; A2A DataParts lose type information
5. **Protocol compliance gaps** — wrong card path, no task cancel, no push notifications, text-only parts

## Scope

This branch builds Layers 0 and 1 (protocol module + qhorus bridges). Engine migration (Layer 2) is casehubio/engine#940.

### Phase 1 Audit Summary

Issue #396 defines two phases: audit (Phase 1) and bridge design (Phase 2). The audit mapped qhorus capabilities against A2A protocol concepts:

| A2A Concept | Qhorus Equivalent | Gap |
|---|---|---|
| Agent Card | `AgentCardResource` (static, infrastructure-focused) | Needs dynamic directory + per-agent cards |
| Task lifecycle | Commitment lifecycle (OPEN → fulfilled/failed/declined) | Mapping exists; needs `input-required` state |
| Parts (text, data, file) | `content` (text only) | No structured data field → add `payload` |
| Streaming | SSE via `A2AChannelBackend` + virtual thread model | Superset — qhorus push model covers this |
| Push notifications | CDI events, `MessageReceivedEvent` | Internal only — external webhook is out of scope |
| Authentication | `CredentialResolver` SPI | Exists; needs per-binding auth config |
| Multi-turn conversation | Channels, commitments, speech acts | Dramatically richer — A2A maps to a subset |
| Outbound delegation | Engine `A2AWorkerFunctionHandler` (bypasses mesh) | Needs channel-based outbound bridge |

The bridge design (Phase 2) below addresses each gap identified in the audit.

### Design Decision Legend

This spec references decisions from the brainstorming phase:

| Tag | Decision |
|---|---|
| D1 | Three-layer architecture (protocol / bridges / engine consumer) |
| D2 | `ExternalAgentBinding` entity for external agent resolution |
| D3 | Platform directory card + per-agent cards |
| D4 | `payload` field for structured data |
| D5 | Payload excluded from normative governance |
| D6 | AT_LEAST_ONCE delivery via delivery pump (not fanOut) |
| D7 | `CredentialResolver` SPI for outbound authentication |

Full rationale for each decision is in `decisions.md`.

---

## Architecture

Three layers with clean separation of concerns (D1):

```
┌──────────────────────────────────────────────────┐
│  Layer 2: Engine (orchestration consumer)          │
│  casehubio/engine#940 — follow-on                  │
│  Delegates via COMMAND/HANDOFF on channels         │
│  No own A2A transport — pure channel consumer      │
└───────────────────────┬──────────────────────────┘
                        │ dispatches messages
┌───────────────────────▼──────────────────────────┐
│  Layer 1: Qhorus (communication mesh)             │
│                                                    │
│  runtime/api/           (inbound, refactored)      │
│  ├── A2AResource        — JAX-RS endpoints          │
│  ├── A2AChannelBackend  — external → channels       │
│  ├── A2AActorResolver   — identity resolution       │
│  ├── A2ATaskStateMapper — commitment → A2A state    │
│  └── AgentCardResource  — dynamic directory + cards  │
│                                                    │
│  a2a-outbound/          (outbound, new module)      │
│  ├── A2AOutboundBackend — channels → external A2A   │
│  ├── A2AInstanceResolver— target → binding → endpoint│
│  └── A2AResponseHandler — A2A response → dispatch    │
└───────────────────────┬──────────────────────────┘
                        │ uses protocol types + client
┌───────────────────────▼──────────────────────────┐
│  Layer 0: casehub-a2a-protocol (pure Java)        │
│  ├── model/  AgentCard, A2ATask, A2APart, ...     │
│  ├── client/ A2AClient, A2AClientRegistry, Auth   │
│  └── server/ JsonRpc parsing utilities             │
│                                                    │
│  No CDI, no Quarkus, no platform dependency.       │
└──────────────────────────────────────────────────┘
```

### Transition Boundaries

During the transition period (before engine#940), two paths coexist:
- **Engine path** (existing): `A2AWorkerFunctionHandler` → engine's `A2AClient` → direct HTTP. Triggered by engine function calls.
- **Channel path** (new): `MessageService.dispatch()` → `A2AOutboundBackend` → protocol `A2AClient`. Triggered by channel message dispatch.

These are distinct code paths — no single delegation is routed through both. The engine path is removed when engine#940 converts all function-based delegations to channel-based COMMAND/HANDOFF messages.

---

## Layer 0: `casehub-a2a-protocol`

New Maven submodule in the qhorus repo with **platform-level Maven coordinates** (`io.casehub:casehub-a2a-protocol`). Pure Java — no CDI, no Quarkus, no platform dependency. Reusable by any Java project.

The module lives in the qhorus repo for development convenience, but its `groupId` is `io.casehub` (not `io.casehub.qhorus`). This keeps the dependency direction clean: both engine and qhorus depend on the protocol module, neither depends on the other. Follows the same pattern as `io.casehub:casehub-platform-api` (where `CredentialResolver` lives).

### Module Structure

```
a2a-protocol/
├── pom.xml
└── src/main/java/io/casehub/a2a/
    ├── model/
    │   ├── AgentCard.java          — discovery model (name, description, url, version, skills, capabilities, authentication, agents[])
    │   ├── AgentSkill.java         — skill descriptor (id, name, description)
    │   ├── AgentCapabilities.java  — capability flags (streaming, pushNotifications)
    │   ├── A2ATask.java            — task lifecycle (id, contextId, status, artifacts, history)
    │   ├── A2ATaskStatus.java      — task status (state, message)
    │   ├── A2ATaskState.java       — enum: SUBMITTED, WORKING, INPUT_REQUIRED, COMPLETED, FAILED, CANCELED
    │   ├── A2APart.java            — sealed interface: TextPart, DataPart, FilePart
    │   ├── A2AMessage.java         — message (role, parts, messageId, taskId, contextId, metadata: Map<String, Object>)
    │   └── A2AArtifact.java        — output artifact (name, parts, index, append)
    ├── client/
    │   ├── A2AClient.java          — JSON-RPC 2.0 HTTP client (send, stream, cancel, fetchAgentCard, checkHealth)
    │   ├── A2AClientRegistry.java  — client lifecycle (getOrCreate, evict, shutdown)
    │   └── AuthConfig.java         — auth config (type: NONE/BEARER/API_KEY, tokenConfigKey)
    └── jsonrpc/
        ├── JsonRpcRequest.java     — JSON-RPC 2.0 request builder
        ├── JsonRpcResponse.java    — JSON-RPC 2.0 response parser
        └── JsonRpcError.java       — standard error codes (-32700 parse, -32600 invalid, -32601 not found, -32602 params, -32603 internal)
```

### Key Design Points

**`A2APart` sealed interface:**
```java
public sealed interface A2APart {
    record TextPart(String text) implements A2APart {}
    record DataPart(String mimeType, JsonNode data) implements A2APart {}
    record FilePart(String uri, String name, String mimeType) implements A2APart {}
}
```

**`A2AClient`** — extracted from engine's existing client, stripped of execution concerns (retry, artifact accumulation). Pure JSON-RPC 2.0 transport:
- `send(A2AMessage, String contextId)` → `A2ATask`
- `stream(A2AMessage, String contextId)` → `Stream<A2ATask>` (SSE event stream)
- `cancel(String taskId)` → void (fire-and-forget)
- `fetchAgentCard()` → `AgentCard`
- `checkHealth()` → boolean

**`stream()` contract:** Returns a `java.util.stream.Stream<A2ATask>` backed by an SSE connection. Callers MUST use try-with-resources — the Stream is `AutoCloseable` and closing it releases the underlying HTTP connection. Unclosed streams leak connections. On virtual threads, the blocking pull-based API is acceptable (one virtual thread per stream). Client-side timeout is the caller's responsibility — configure via `java.net.http.HttpClient.Builder.connectTimeout()` and enforce a max-duration deadline in the calling code.

**Timeout:** The client configures HTTP-level timeouts via `java.net.http.HttpClient.Builder.connectTimeout()`. Per-request read timeouts are set on the `HttpRequest`. The delivery pump's health tracking (circuit breaker, backoff) provides the operational timeout mechanism for outbound calls. Retry is NOT in the client — it belongs in the delivery pump.

**JSON-RPC 2.0 compliance:** Full error code support (-32700 through -32603). Batch requests out of scope (not needed for A2A).

### Dependencies

- Jackson Databind (JSON processing) — no other external dependency
- Java 21+ (sealed interfaces, records)
- java.net.http.HttpClient (JDK built-in)

---

## Layer 1a: Inbound Bridge (Refactored)

### `A2AResource` Refactor

**Drops inline records.** `A2AMessage`, `A2APart`, `Task`, `TaskStatus`, `SendMessageRequest`, `SendMessageResponse` — all replaced by Layer 0 model types.

**JSON-RPC dispatch — two JAX-RS methods** (JAX-RS requires distinct methods for `Response` vs `SseEventSink` return models):

```java
@POST
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public Response dispatch(JsonRpcRequest request) {
    // Route based on request.method():
    //   "message/send"  → sendMessage logic (existing)
    //   "tasks/get"     → getTask logic (existing)
    //   "tasks/cancel"  → cancelTask logic (new)
    // Unknown method → JsonRpcError -32601 (Method not found)
}

@POST
@Consumes(MediaType.APPLICATION_JSON)
@Produces("text/event-stream")
@RunOnVirtualThread
public void dispatchStream(JsonRpcRequest request,
                           @Context SseEventSink sink,
                           @Context Sse sse) {
    // Only "message/send" supports SSE streaming
    // Other methods → send JsonRpcError -32600 as SSE error event, close sink
    // Reuses shared routing logic with dispatch()
}
```

JAX-RS content negotiation routes based on `Accept` header: `application/json` → `dispatch()`, `text/event-stream` → `dispatchStream()`. The SSE method requires `void` return with `@Context SseEventSink` injection and `@RunOnVirtualThread` (matching the existing `streamTask()` pattern). Shared routing logic is factored into a private method.

This replaces the REST-style endpoints (`/a2a/message:send`, `/a2a/tasks/{id}`) with a JSON-RPC 2.0 compliant endpoint at `POST /a2a`. Only `message/send` supports SSE streaming.

**`tasks/cancel`** maps to `commitmentService.decline()` → A2A "canceled" state (per `a2a-decline-maps-to-cancelled` protocol).

**JSON-RPC error responses** use standard codes from Layer 0 `JsonRpcError`.

### Agent Card Resource (D3)

**Platform directory card** at `/.well-known/agent.json`:
```json
{
  "name": "Qhorus Agent Platform",
  "description": "Multi-agent communication mesh with normative governance",
  "url": "https://...",
  "version": "0.2-SNAPSHOT",
  "skills": [
    {
      "id": "delegate-task",
      "name": "Task Delegation",
      "description": "Delegate work to registered agents via typed channels"
    }
  ],
  "capabilities": { "streaming": true, "pushNotifications": false },
  "authentication": { "schemes": ["bearer"] },                         // A2A standard — declares accepted auth mechanisms
  "tenancyId": "default",                                              // Qhorus extension — retained for multi-tenant discovery
  "agents": [
    { "name": "code-reviewer", "url": "/.well-known/agents/code-reviewer.json" },
    { "name": "embabel-analyst", "url": "/.well-known/agents/embabel-analyst.json" }
  ]
}
```

**Per-agent cards** at `/.well-known/agents/{instanceId}.json`:
- Internal agents: generated from instance registry (capabilities → skills)
- External agents (via D2 `ExternalAgentBinding`): proxied from their real endpoint, cached

### `A2ATaskStateMapper` (renamed from `A2ATaskState`)

Unchanged logic. Renamed for clarity — it's a mapper, not a state enum. The state enum moves to Layer 0 (`A2ATaskState`).

### Inbound Content Mapping (D4)

A2A message parts → qhorus message fields:
- **TextPart(s)** → `content` (multiple TextParts concatenated with `\n\n`)
- **DataPart(s)** → `payload` (single DataPart: direct; multiple: wrapped in JSON array with `mimeType`/`data` per element)
- **FilePart (URI/large)** → SharedData + ArtefactRef
- **FilePart (small/inline)** → `payload` entry

**Known limitation — lossy round-trip:** Multiple TextParts with different roles/contexts are concatenated into a single `content` string. On outbound (reverse mapping), they become a single TextPart. If a message is relayed through qhorus (inbound from Agent A → outbound to Agent B), the part structure degrades. This is an accepted boundary translation cost — qhorus's internal model (says/carries/references) prioritises semantic clarity over A2A structural fidelity.

---

## Layer 1b: Outbound Bridge (New Module)

New optional submodule `a2a-outbound/`. Activates by classpath presence, following `slack-channel/` and `connector-backend/` pattern.

### Module Structure

```
a2a-outbound/
├── pom.xml
└── src/main/java/io/casehub/qhorus/a2a/outbound/
    ├── A2AOutboundBackend.java       — ChannelBackend impl (AT_LEAST_ONCE)
    ├── A2AInstanceResolver.java      — target → ExternalAgentBinding → endpoint
    ├── A2AResponseHandler.java       — A2A response → channel message dispatch
    └── ExternalAgentBindingResource.java — REST API for binding management
```

**Type placement follows the `ChannelConnectorBinding` pattern:**

| Type | Module | Rationale |
|---|---|---|
| `ExternalAgentBinding` (record) | `api/` | Domain type — available to all modules |
| `ExternalAgentBindingEntity` (JPA) | `runtime/` | In Hibernate's package scan — JPQL always resolves |
| `ExternalAgentBindingStore` (interface) | `api/` | Store SPI — available to all modules |
| `JpaExternalAgentBindingStore` | `runtime/` | JPA implementation |
| `InMemoryExternalAgentBindingStore` | `persistence-memory/` | Test/in-memory implementation |
| `ExternalAgentBindingResource` | `a2a-outbound/` | REST API — only needed when module is active |

This ensures the runtime JPQL subquery in `InstanceService.markStaleOlderThan()` can reference `ExternalAgentBindingEntity` regardless of whether `a2a-outbound` is on the classpath. The entity is registered with Hibernate's persistence unit via `runtime/`'s package scan (per `optional-module-jpa-package-registration` protocol).

### `ExternalAgentBinding` (D2)

Follows the `ChannelConnectorBinding` pattern — lightweight join entity referencing Instance by ID:

```java
public record ExternalAgentBinding(
    UUID id,
    String instanceId,
    String endpoint,
    String authConfigKey,
    String protocolVersion,    // "1.0" default
    Instant createdAt
) {}
```

**Lifecycle exemption:** Instances with an active `ExternalAgentBinding` are exempt from stale tracking. `InstanceService.markStaleOlderThan()` skips them. External agent liveness is verified at delivery time via the delivery pump's health tracking.

**Flyway migration:** V42 — `external_agent_binding` table with FK to instance, unique constraint on instanceId. Located in `runtime/src/main/resources/db/qhorus/migration/V42__external_agent_binding.sql` (runtime module, not a2a-outbound module). All qhorus Flyway migrations share the `db/qhorus/migration/` location — optional modules (slack-channel, connector-backend) do not have their own migration locations. The table exists regardless of whether a2a-outbound is on the classpath, avoiding Flyway out-of-order versioning issues.

**Stale tracking exemption:** `InstanceService.markStaleOlderThan()` is modified to skip instances with an active `ExternalAgentBinding`. External agents don't heartbeat — their liveness is verified at delivery time by the delivery pump's health tracking (circuit breaker, backoff).

The existing implementation uses Panache HQL (`InstanceEntity.update("status = 'stale' WHERE lastSeen < ?1 AND status = 'online'", cutoff)`), which doesn't support LEFT JOIN syntax. The exemption uses a JPQL subquery:
```java
InstanceEntity.update(
    "status = 'stale' WHERE lastSeen < ?1 AND status = 'online' " +
    "AND instanceId NOT IN (SELECT eab.instanceId FROM ExternalAgentBindingEntity eab)",
    cutoff);
```

### `A2AOutboundBackend` (D1, D6)

Implements `ChannelBackend` with `AT_LEAST_ONCE` delivery guarantee. Delivery is handled by the existing `DeliveryService` pump — not `fanOut()` inline virtual threads.

```java
@ApplicationScoped
public class A2AOutboundBackend implements ChannelBackend {

    @Override public String backendId() { return "a2a-outbound"; }
    @Override public ActorType actorType() { return ActorType.AGENT; }
    @Override public DeliveryGuarantee deliveryGuarantee() { return DeliveryGuarantee.AT_LEAST_ONCE; }

    @Override
    public void post(ChannelRef channel, OutboundMessage message) {
        // Selective interception:
        // 1. Check if message.target() resolves to an external A2A agent
        //    via ExternalAgentBindingStore lookup
        // 2. If not external → return (message is for internal agents)
        // 3. If external → forward via A2AClient from protocol module
        // 4. Dispatch response back as channel message
    }
}
```

### Outbound Routing Semantics

The outbound backend intercepts messages based on `target`:

| Message Type | `target` required? | Outbound A2A trigger |
|---|---|---|
| COMMAND | Optional | If `target` resolves to an external agent via `ExternalAgentBindingStore` |
| HANDOFF | Mandatory | If `target` resolves to an external agent — delegation semantics preserved |
| STATUS, DONE, FAILURE, DECLINE | Never targeted at external | Skipped — these are responses, not outbound requests |
| EVENT | No target by protocol | Skipped |
| All others | — | Only if `target` is present AND resolves to an external agent |

Messages without `target` are never forwarded outbound — they are broadcast to internal participants only. The `target` field is the sole routing discriminator for outbound A2A delivery.

### Delivery Pump Integration

`fanOut()` explicitly skips backends with `deliveryGuarantee() == AT_LEAST_ONCE` when the delivery pump is enabled (verified: `ChannelGateway.fanOut()` line 260). The outbound backend's `post()` is NEVER called by `fanOut()`. It is called exclusively by `DeliveryBatchExecutor.deliverBatch()` via `postTracked()` → `post()`.

**Overhead pattern:** The delivery pump reads ALL messages from the channel (cursor-based), not just messages targeted at external agents. Every message on a channel with a registered outbound backend triggers a `post()` call. The backend's selective interception (step 1: check if target resolves to external agent) makes most `post()` calls no-ops. This is functionally correct — the per-message `ExternalAgentBindingStore` lookup is a cache-backed operation.

**Cursor initialisation:** `DeliveryBatchExecutor.initializeCursor()` sets `lastDeliveredId` to the current head (latest message ID). When the outbound backend first registers on a channel, it will NOT retroactively deliver old messages — only new messages from that point forward. This is intentional: external agents are added prospectively; retroactive delivery of historical messages to a newly-bound agent would be semantically wrong (stale context).

### Self-Referential Loop Prevention

When the backend dispatches a response back to the channel, that dispatch persists a new message and calls `fanOut()`. `fanOut()` returns `hasTracked=true` (because A2AOutboundBackend is AT_LEAST_ONCE), which signals the delivery pump. The pump processes the response message through `post()` again — but the sender-based guard skips it: the backend checks if `message.sender()` matches a known external agent instanceId and returns early.

This pattern is a **sender-based guard** — functionally distinct from `ConnectorChannelBackend.post()` which uses a **channel-binding cache guard** (checking `cache.get(channel.id())` for a connector binding). The A2A outbound backend cannot use a channel-binding guard because it intercepts selectively by target, not by channel membership.

**Pump cycle depth is 2:** Original message → outbound HTTP → response dispatch → pump picks up response → sender guard skips → done. Commitment state transitions (`CommitmentStateChangedEvent`) fired by the response do NOT create new messages by default — observers react to commitment state, not by dispatching messages. The cycle is bounded.

### Dual-Backend Lifecycle

The inbound `A2AChannelBackend` (BEST_EFFORT) and outbound `A2AOutboundBackend` (AT_LEAST_ONCE) both declare `actorType() → ActorType.AGENT`. Both may be registered on the same channel when external A2A agents participate.

On every message dispatch to such a channel:
- `A2AChannelBackend.post()` fires inline via `fanOut()` (BEST_EFFORT) — dispatches to SSE consumers
- `A2AOutboundBackend.post()` fires via the delivery pump (AT_LEAST_ONCE) — forwards to external agents

The two backends have independent lifecycles:
- `A2AChannelBackend` registers lazily via `ensureRegistered()` on first inbound A2A message or SSE subscription
- `A2AOutboundBackend` registers when an `ExternalAgentBinding` is created for a channel member, or on startup recovery

Either backend can exist independently on a channel. A channel with only inbound SSE consumers has only `A2AChannelBackend`. A channel with only outbound external agents has only `A2AOutboundBackend`. A channel with both has both.

### Registration and Restart Recovery (resolves ADR-0013)

**Registration triggers:**
1. An `ExternalAgentBinding` is created for an instance that is a member of a channel
2. A message targets an instance with an active binding
3. On startup via `@Observes ChannelInitialisedEvent` — queries `ExternalAgentBindingStore` for active bindings on the initialised channel; registers if any exist

The startup recovery (trigger 3) resolves the limitation documented in ADR-0013. The `ExternalAgentBinding` entity provides the persistent selection criterion that ADR-0013 identified as the correct fix (Option C). Only channels with active bindings trigger registration — no spurious registrations on non-A2A channels.

The inbound `A2AChannelBackend` also benefits: it should observe `ChannelInitialisedEvent` and re-register on channels with active bindings, so SSE consumers can reconnect after restart and receive events immediately. ADR-0013 status → **Superseded** by this spec.

### `A2AResponseHandler`

Translates A2A responses back into qhorus channel messages:

| A2A Task State | Qhorus MessageType | Commitment |
|---|---|---|
| `completed` | DONE | Fulfilled |
| `failed` | FAILURE | Failed |
| `canceled` | DECLINE | Declined |
| `working` | STATUS | (no transition) |
| `input-required` | STATUS + `payload: {"input_required": true}` | (no transition) |

**Outbound content mapping** (reverse of inbound):
- `content` → TextPart
- `payload` → DataPart(s)
- ArtefactRefs → FilePart(s) with SharedData URIs

**A2A artifact mapping:** When a `completed` task includes `artifacts` (list of `A2AArtifact` with `name, parts, index`), their parts follow the same inbound content mapping rules and are merged into the DONE message:
- Artifact TextParts → appended to DONE `content` (separated by `\n\n`)
- Artifact DataParts → merged into DONE `payload` as a JSON array with per-artifact metadata (`name`, `index`, `data`)
- Artifact FileParts → additional ArtefactRefs on the DONE message via SharedData

This keeps artifacts visible on the channel as part of the completion message. The artifact metadata (`name`, `index`) is preserved in the payload structure, so consumers can distinguish artifact data from message data.

### Authentication (D7)

Outbound HTTP calls use `CredentialResolver` SPI for token resolution. Each `ExternalAgentBinding` stores an `authConfigKey`. The resolver provides the actual credential at call time — supports static tokens, environment variables, and vault-backed implementations.

Supported auth types:
- **Bearer token** — `Authorization: Bearer <token>`
- **API key** — `X-API-Key: <key>`
- **OAuth2 Client Credentials** — if `CredentialResolver` SPI needs extension for multi-field auth configs, this is a follow-on.

---

## Message Model Change (D4, D5)

### New `payload` Field

```java
// Message API record (actual 18-field shape with new payload field)
public record Message(
    Long id,
    UUID channelId,
    String sender,
    MessageType messageType,   // actual field name (not "type")
    ActorType actorType,
    String tenancyId,
    String content,            // what the message SAYS (text)
    String payload,            // what the message CARRIES (structured data, raw JSON) ← NEW
    String correlationId,
    Long inReplyTo,
    int replyCount,
    List<ArtefactRef> artefactRefs,
    String target,
    String topic,
    UUID commitmentId,
    Instant deadline,
    Instant acknowledgedAt,
    int version,
    Instant createdAt
) {}
```

**`payload` type is `String`, not `JsonNode`.** This keeps `qhorus-api` free of Jackson compile-time dependency — currently zero files in qhorus-api or qhorus-runtime import `com.fasterxml.jackson.databind.JsonNode`. The database stores payload as JSONB (PostgreSQL validates JSON on write). The A2A bridge layer (which already has Jackson) performs JsonNode ↔ String conversion at the protocol boundary. Internal consumers that need structured access parse the string — most just carry it through.

**Schema:** `ALTER TABLE message ADD COLUMN payload JSONB` (PostgreSQL). JPA mapping uses `@JdbcTypeCode(SqlTypes.JSON)` on a `String` field for dialect portability (Hibernate resolves to JSONB on PostgreSQL, TEXT/JSON on H2).

**Flyway migration:** V43 — `message.payload` column (nullable).

### Propagation

| Carrier | Change |
|---|---|
| `MessageDispatch` | Add `.payload(String)` to builder |
| `OutboundMessage` | Add `payload` field (`String`, backward-compatible constructor with null default) |
| `MessageView` | Add `payload` field |
| `MessageReceivedEvent` | Add `payload` field |
| `NormalisedMessage` | Add `payload` field |
| `MessageLedgerEntry` | Add `payload` column (for audit completeness) |
| `MessageSummary` (MCP) | Add `payload` in response |

Note: `DeliveryBatchExecutor.toOutbound()` must also propagate `payload` from `Message` to `OutboundMessage` — currently it constructs `OutboundMessage` without the field.

### Governance Scope (D5)

Payload is **excluded** from normative governance:
- Watchdog text analysis (Jaccard similarity) operates on `content` only
- Commitment tracking on `content` only
- Ledger records `payload` for audit trail but does not analyze it
- Protocol enforcement on `content` only

---

## Protocol Compliance Fixes

| Gap | Fix |
|---|---|
| Agent card path `agent-card.json` | → `agent.json` per A2A spec |
| Hardcoded skills | → Dynamic directory card + per-agent cards (D3) |
| No auth section in card | → Add `authentication` field from config |
| Text-only parts | → Full TextPart/DataPart/FilePart support (D4) |
| No `tasks/cancel` | → `tasks/cancel` method in JSON-RPC dispatch |
| No `input-required` state | → STATUS with `payload: {"input_required": true}` |
| JSON-RPC partial (REST-style paths) | → Full 2.0: single `POST /a2a` dispatch endpoint with method routing |
| Push notifications | → Out of scope (webhook-observer module covers this pattern) |

---

## Testing Strategy

### Layer 0 Tests (pure JUnit, no Quarkus)

- `A2AClientTest` — mock HTTP responses, verify JSON-RPC request format, error handling
- `A2APartTest` — serialization/deserialization of sealed part types
- `AgentCardTest` — parsing, `agents` array, round-trip
- `JsonRpcErrorTest` — standard error code mapping

### Layer 1a Tests (inbound, `@QuarkusTest`)

- `A2AResourceTest` — existing tests updated to use Layer 0 types; new tests for:
  - JSON-RPC method routing (`message/send`, `tasks/get`, `tasks/cancel`; unknown method → `-32601`)
  - Content negotiation (`Accept: application/json` → sync, `Accept: text/event-stream` → SSE)
  - SSE method restriction (`dispatchStream()` with `tasks/get` or `tasks/cancel` → error event, sink closed)
  - JSON-RPC error responses (malformed request → `-32700`, invalid params → `-32602`)
- `AgentCardResourceTest` — directory card with `agents` array; per-agent card generation; proxied external card
- `A2ATaskStateMapperTest` — renamed from `A2ATaskStateTest`, same assertions

### Layer 1b Tests (outbound, `@QuarkusTest`)

- `A2AOutboundBackendTest` — CDI-free unit tests: selective interception (external vs internal targets), self-referential loop prevention, delivery guarantee assertion
- `ExternalAgentBindingStoreContractTest` — abstract base + InMemory runner (standard store pattern)
- `A2AResponseHandlerTest` — state mapping, content reverse-mapping
- `A2AOutboundIntegrationTest` — end-to-end: register external agent → dispatch COMMAND → mock A2A response → verify DONE arrives on channel with commitment fulfilled

### Message Model Tests

- `MessagePayloadTest` — dispatch with payload, verify persistence and retrieval
- `PayloadGovernanceTest` — verify watchdog Jaccard analysis ignores payload
- `OutboundMessagePayloadTest` — verify payload propagation through fanOut

---

## Files Changed / Created

### New Module: `a2a-protocol/`
| File | What |
|---|---|
| `pom.xml` | Maven module config |
| `model/*.java` (8 files) | A2A data types |
| `client/*.java` (3 files) | HTTP client + registry + auth |
| `jsonrpc/*.java` (3 files) | JSON-RPC utilities |
| Tests (~8 files) | Pure JUnit |

### New Module: `a2a-outbound/`
| File | What |
|---|---|
| `pom.xml` | Maven module config (depends on a2a-protocol, qhorus-api) |
| `A2AOutboundBackend.java` | ChannelBackend impl |
| `A2AInstanceResolver.java` | Target resolution |
| `A2AResponseHandler.java` | Response → channel dispatch |
| `ExternalAgentBindingResource.java` | REST API for binding management |
| Tests (~6 files) | Unit + integration |

### Modified: `runtime/`
| File | Change |
|---|---|
| `A2AResource.java` | Restructure as JSON-RPC 2.0 dispatch (sync + SSE methods); drop inline records; use Layer 0 types |
| `AgentCardResource.java` | Directory card + per-agent cards; path `agent.json`; add `authentication` field |
| `A2ATaskState.java` | Rename to `A2ATaskStateMapper.java`, delegate state enum to Layer 0 |
| `A2AChannelBackend.java` | Use Layer 0 `A2AMessage` type; add `@Observes ChannelInitialisedEvent` for restart recovery |

### Modified: `api/`
| File | Change |
|---|---|
| `Message.java` | Add `payload` field (String, nullable) |
| `MessageDispatch.java` | Add `.payload(String)` builder method |
| `OutboundMessage.java` | Add `payload` field |
| `MessageView.java` | Add `payload` field |
| `MessageReceivedEvent.java` | Add `payload` field |
| `NormalisedMessage.java` | Add `payload` field |
| `ExternalAgentBinding.java` | New — binding record (follows `ChannelConnectorBinding` pattern) |
| `ExternalAgentBindingStore.java` | New — store SPI (follows `ChannelBindingStore` pattern) |

### Modified: `runtime/` (message model + binding entity)
| File | Change |
|---|---|
| `ExternalAgentBindingEntity.java` | New — JPA entity (in runtime for Hibernate package scan) |
| `JpaExternalAgentBindingStore.java` | New — JPA store implementation |
| `MessageEntity.java` | Add `payload` column with `@JdbcTypeCode(SqlTypes.JSON)` |
| `MessageService.java` | Pass payload through dispatch |
| `V42__external_agent_binding.sql` | Flyway migration — `external_agent_binding` table |
| `V43__message_payload.sql` | Flyway migration — `message.payload` column |
| `QhorusMcpTools.java` | Add `payload` parameter to `send_message` |
| `QhorusEntityMapper.java` | Map payload in `toMessageView()`, `toMessageSummary()` |
| `LedgerWriteService.java` | Record payload on `MessageLedgerEntry` |

### Modified: `persistence-memory/`
| File | Change |
|---|---|
| `InMemoryExternalAgentBindingStore.java` | New — in-memory store implementation |
| `ExternalAgentBindingStoreContractTest.java` | New — contract test runner (abstract base in `persistence-memory/test`) |

### Modified: root `pom.xml`
| Change |
|---|
| Add `a2a-protocol` and `a2a-outbound` as submodules |

---

## Out of Scope

| Item | Tracked |
|---|---|
| Engine A2A migration | casehubio/engine#940 |
| Push notification webhooks | Existing `webhook-observer` module covers this pattern |
| OAuth2 Client Credentials (multi-field auth) | Follow-on if CredentialResolver SPI needs extension |
| Reactive outbound backend | No reactive A2A backend exists yet |
| A2A batch JSON-RPC requests | Not needed for agent-to-agent communication |

---

## References

- `runtime/src/main/java/io/casehub/qhorus/runtime/api/A2AResource.java` — existing inbound resource
- `runtime/src/main/java/io/casehub/qhorus/runtime/api/A2AChannelBackend.java` — existing inbound backend
- `runtime/src/main/java/io/casehub/qhorus/runtime/api/AgentCardResource.java` — existing static agent card
- `io.casehub.engine.a2a.A2AClient` — engine client (extraction source for protocol module)
- `connector-backend/` — optional module activation pattern
- `slack-channel/` — AT_LEAST_ONCE delivery + CredentialResolver pattern
- `runtime/src/main/java/io/casehub/qhorus/runtime/gateway/DeliveryService.java` — delivery pump
- `api/src/main/java/io/casehub/qhorus/api/gateway/ChannelBackend.java` — SPI
- `api/src/main/java/io/casehub/qhorus/api/channel/ChannelConnectorBinding.java` — binding entity pattern
- `docs/specs/2026-05-13-a2a-integration-design.md` — original A2A integration spec
- `docs/specs/2026-06-13-a2a-sse-streaming.md` — SSE streaming spec
- `docs/protocols/casehub/a2a-decline-maps-to-cancelled.md` — DECLINE → "canceled" protocol
- `docs/protocols/casehub/sse-active-model-virtual-thread.md` — SSE threading protocol
- `docs/protocols/casehub/sse-keepalive-named-event.md` — keepalive protocol
- [A2A Protocol specification](https://github.com/google/A2A) — targeting v0.2.1 (2025-05 release). Method names: `message/send`, `tasks/get`, `tasks/cancel`
- casehubio/engine#940 — engine follow-on issue
