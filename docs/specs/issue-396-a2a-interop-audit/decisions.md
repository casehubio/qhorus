# Decisions — #396 A2A Interop

## D1: A2A Architecture — Three-Layer Decomposition

**Choice:** Three-layer architecture with clean separation of concerns:
- **Layer 0** (`casehub-a2a-protocol`) — pure A2A protocol types + HTTP client, no CDI/Quarkus/platform dependency. New Maven submodule in the qhorus repo.
- **Layer 1** (qhorus) — inbound bridge (existing, refactored) + outbound bridge (new optional module `casehub-qhorus-a2a-outbound`). All A2A transport lives here. The outbound backend declares `AT_LEAST_ONCE` delivery guarantee and is served by the existing delivery pump (`DeliveryService`), not by `fanOut()` inline virtual threads (see D6).
- **Layer 2** (engine) — pure channel consumer. Delegates to external agents via COMMAND/HANDOFF messages. No own A2A transport. Engine migration is a follow-on issue.

**Alternatives:**
- Keep split as-is (inbound qhorus / outbound engine) — leaves external delegation invisible to the mesh; no ledger, no commitments, no observers
- Move everything into qhorus including execution concerns — mixes orchestration into the communication layer
- Shared protocol module only, no outbound backend — doesn't solve the channel visibility gap

**Rationale:** Every external A2A interaction should flow through channels — ledger recorded, commitment tracked, observers notified. The engine's current A2AWorkerFunctionHandler bypasses the mesh entirely. With an outbound ChannelBackend, external delegation becomes a normal channel conversation. The engine's timeout/retry/artifact concerns all have cleaner qhorus equivalents (Commitment.expiresAt, transport-level retry, SharedData + ArtefactRef). The engine's entire a2a package (6 classes) becomes unnecessary once migration completes.

**Trade-offs:** Engine migration is deferred to a follow-on issue. During the transition period, the engine's A2AWorkerFunctionHandler still works but bypasses channels. Two A2A clients coexist temporarily (protocol module + engine's own).

**Transition boundaries:** The engine's `A2AWorkerFunctionHandler` is triggered by engine function calls (tool invocations). The new outbound backend is triggered by channel message dispatch. These are distinct code paths — no single delegation is routed through both. The engine migration issue converts function-based delegations to channel-based COMMAND/HANDOFF messages. In-flight engine A2A calls complete normally; the engine's A2A package is removed only when all function-based delegations have been converted.

**Sources:**
- `runtime/src/main/java/io/casehub/qhorus/runtime/api/A2AChannelBackend.java` — existing inbound bridge
- `runtime/src/main/java/io/casehub/qhorus/runtime/api/A2AResource.java` — existing JAX-RS endpoints with inline records
- `io.casehub.engine.a2a.A2AClient` — engine's current outbound client (6 classes total)
- `io.casehub.engine.a2a.A2AWorkerFunctionHandler` — engine's execution adapter that bypasses channels
- `connector-backend/` — pattern for optional channel backend modules
- `slack-channel/` — pattern for optional channel backend modules
- `api/src/main/java/io/casehub/qhorus/api/gateway/ChannelBackend.java` — SPI that outbound backend implements
- `runtime/src/main/java/io/casehub/qhorus/runtime/gateway/DeliveryService.java` — delivery pump for AT_LEAST_ONCE backends

**Exploration:** deep-analysis
**Status:** captured

## D2: External Agent Resolution — ExternalAgentBinding Entity

**Choice:** External A2A agents are registered as normal instances (with capability tags for discovery) and their A2A-specific routing information lives in a separate `ExternalAgentBinding(instanceId, endpoint, authConfigKey, protocolVersion)` entity. The binding follows the established `ChannelConnectorBinding` pattern — a lightweight join entity that references Instance by ID without modifying the Instance entity.

External agent resolution: the outbound backend looks up the target instanceId in the `ExternalAgentBindingStore`, reads the endpoint and auth configuration, and makes the A2A HTTP call via the delivery pump.

Capability-based routing (`target: "capability:code-review"`) resolves through existing instance capability tags. Once a target instance is resolved, the binding provides the A2A endpoint.

Lifecycle: external agents are exempt from stale tracking. `InstanceService.markStaleOlderThan()` skips instances that have an active `ExternalAgentBinding`. External agent liveness is verified at delivery time — the delivery pump's health tracking handles unreachable endpoints (circuit breaker, backoff).

**Alternatives:**
- Instance metadata fields (`a2a.endpoint`, `a2a.auth.type`) — Instance entity has no metadata storage; adding it changes a core domain entity across the entire stack (4 store implementations, all builders, all test utilities). Disproportionate to the need.
- Dedicated `A2AOutboundRegistry` — decoupled from instance registry, but duplicates discovery infrastructure and external agents are invisible to capability-based routing
- Channel binding (one channel per external agent) — follows connector-backend pattern but forces a rigid 1:1 channel-to-agent mapping; no multi-agent channels with mixed internal/external participants
- Configuration-based (`quarkus.qhorus.a2a.agents.*.endpoint=...`) — too rigid for dynamic A2A ecosystems where agents register/deregister at runtime; SmallRye Config doesn't support runtime mutation. Suitable for seeding known agents at startup (complementary, not alternative).

**Rationale:** Follows the established `ChannelConnectorBinding` pattern: a binding entity that links a core entity (Instance) to protocol-specific routing data without modifying the core entity. Instance stays clean of protocol concerns. The "is this instance an external A2A agent?" question is answerable by binding existence. The binding can carry richer data (protocol version, card cache TTL) without further Instance changes.

**Trade-offs:** New entity, Flyway migration, store interface, 3 store implementations (JPA, reactive, in-memory). But this is the same scope as `ChannelConnectorBinding` — an established, well-tested pattern.

**Depends on:** D1 (three-layer architecture)
**Sources:**
- `api/src/main/java/io/casehub/qhorus/api/channel/ChannelConnectorBinding.java` — established binding pattern
- `api/src/main/java/io/casehub/qhorus/api/store/ChannelBindingStore.java` — store SPI pattern
- `runtime/src/main/java/io/casehub/qhorus/runtime/instance/InstanceEntity.java` — Instance entity (no metadata fields)
- `runtime/src/main/java/io/casehub/qhorus/runtime/instance/InstanceService.java` — stale tracking scheduler
- `runtime/src/main/java/io/casehub/qhorus/runtime/instance/CapabilityEntity.java` — capability tags (flat strings)
**Exploration:** quick → revised after review
**Status:** revised (R1-05, R1-06, R1-07: replaced instance metadata with ExternalAgentBinding entity following ChannelConnectorBinding pattern; added lifecycle exemption for external agents)

## D3: Agent Card Strategy — Platform Directory Card + Per-Agent Cards

**Choice:** Two tiers of agent cards:
- **Platform directory card** at `/.well-known/agent.json` — describes qhorus as a multi-agent platform. Includes an `agents` array listing per-agent card URLs for discovery. Skills describe platform-level delegation capabilities, not infrastructure primitives.
- **Per-agent cards** at `/.well-known/agents/{instanceId}.json` — describes specific registered agents and their delegatable capabilities. External A2A agents registered via D2 get proxied cards (fetched from their real endpoint via `ExternalAgentBinding`, cached).

Discovery flow: external orchestrator → `/.well-known/agent.json` → reads `agents` array → fetches individual agent cards → delegates to specific agents.

**Alternatives:**
- Infrastructure-focused platform card (original D3) — skills like "channel-messaging" and "shared-data" describe the bus, not the passengers; external orchestrators can't delegate work to infrastructure capabilities
- Dynamic single card from instance registry — mixes platform capabilities with individual agent capabilities in one flat list; external orchestrators can't target specific agents
- Per-agent cards only (no platform card) — no entry point for discovery; violates A2A convention that `/.well-known/agent.json` is the discovery URL

**Rationale:** The platform card is the discovery entry point per A2A convention. Making it a directory card solves the semantic mismatch: the platform card links to agents that can handle delegated tasks, rather than describing infrastructure primitives. The `agents` array is a standard link-relation pattern — the orchestrator discovers the index, then drills into specific capabilities.

**Trade-offs:** Platform card is partially dynamic (agents array changes as instances register/deregister). Proxied cards need cache invalidation when external agent capabilities change. Platform card URL path corrected from `agent-card.json` to `agent.json` per A2A spec.

**Depends on:** D2 (ExternalAgentBinding for external agent endpoint resolution)
**Sources:**
- `runtime/src/main/java/io/casehub/qhorus/runtime/api/AgentCardResource.java` — current hardcoded card (infrastructure skills)
- A2A spec: `/.well-known/agent.json`
**Exploration:** quick → revised after review
**Status:** revised (R1-11, R1-12: platform card becomes agent directory with `agents` array for discovery; infrastructure skills replaced with delegation-oriented framing)

## D4: Message Content Model — Add `payload` Field for Structured Data

**Choice:** Add a nullable `payload` field (JSONB column) to Message for structured data. Three distinct content concerns, three distinct fields:
- `content` (String, existing) — what the message **says** (text, natural language)
- `payload` (JsonNode, new, nullable) — what the message **carries** (structured data, machine-readable)
- `artefactRefs` (List\<ArtefactRef\>, existing) — what the message **references** (long-lived resources with claim/release lifecycle)

A2A mapping: TextPart → content (multiple TextParts concatenated with `\n\n`), DataPart → payload, FilePart (large/URI) → ArtefactRef, FilePart (small/inline) → payload entry.

DataPart merge strategy: a single DataPart maps directly to `payload`. Multiple DataParts are wrapped in a JSON array, each element an object with `mimeType`, `schema` (if present), and `data` fields — preserving per-part metadata without lossy merging.

Schema: `ALTER TABLE message ADD COLUMN payload JSONB` (PostgreSQL, nullable, backward compatible). JPA mapping uses `@JdbcTypeCode(SqlTypes.JSON)` for dialect portability (Hibernate resolves to JSONB on PostgreSQL, TEXT/JSON on H2). Additive change — existing messages have `payload = null`.

Propagation: MessageDispatch gains `.payload(JsonNode)`, OutboundMessage gains `payload`, MessageView gains `payload`, MessageReceivedEvent gains `payload`. Governance infrastructure (commitments, ledger, protocols, watchdogs) is unaffected — payload is opaque to governance (see D5).

**Alternatives:**
- Text + ArtefactRef only (no model change) — forces ephemeral structured data through ArtefactRef's claim/release lifecycle (semantically wrong); DataParts lose their typed nature when crammed into content String
- Multi-part message model (`content` → `List<MessagePart>`) — full A2A parity but architecturally wrong for qhorus:
  1. **Semantic clarity lost:** The three-field model maps to communication primitives (says/carries/references). A flat part list collapses this distinction — consumers must iterate and filter by type to find what they need.
  2. **Common-case penalty:** Most messages are text. `message.content()` is direct access. `message.parts().stream().filter(TextPart.class::isInstance).findFirst()` penalises the common case.
  3. **Type safety lost:** Each field has the correct type for its role — `String` for text, `JsonNode` for structured data, `List<ArtefactRef>` for lifecycle-managed references. A `List<MessagePart>` is `Object` in disguise.
  4. **Governance incoherence:** Watchdogs (Jaccard similarity), commitment tracking, and ledger analysis operate on text content. With multi-part, every governance consumer must locate and extract text from parts — an ongoing source of bugs and missed analysis.

**Rationale:** Fixes a pre-existing design gap — tool results and structured agent output currently have no proper home in the message model. Today, structured data gets crammed into `content` as a JSON string and consumers guess whether to parse it. The `payload` field gives structured data a typed home. The three-field model preserves semantic clarity and direct access patterns. The A2A bridge translates at the protocol boundary — which is where protocol translation belongs.

**Trade-offs:** One more field on Message and its carriers (MessageDispatch, OutboundMessage, MessageView, MessageReceivedEvent). Internal agents that currently put structured data in `content` can continue to do so — adoption is gradual. The A2A-to-qhorus mapping is lossy for edge cases (multiple TextParts merged, DataPart metadata in wrapper array) — this is acceptable boundary translation.

**Depends on:** D1 (three-layer architecture — A2A bridge needs payload for DataPart mapping)
**Sources:**
- `api/src/main/java/io/casehub/qhorus/api/message/Message.java` — current message record (no payload field)
- `api/src/main/java/io/casehub/qhorus/api/message/MessageDispatch.java` — dispatch builder
- `api/src/main/java/io/casehub/qhorus/api/gateway/OutboundMessage.java` — fan-out carrier
- `api/src/main/java/io/casehub/qhorus/api/message/ArtefactRef.java` — existing reference model (not suitable for ephemeral data)
**Exploration:** deep-analysis → revised after review
**Status:** revised (R1-15: multi-part rejection reframed on design grounds; R1-16: TEXT→JSONB with dialect portability; R1-18: DataPart merge strategy specified)

## D5: Payload Governance Scope

**Choice:** Payload is excluded from normative governance. Watchdog text analysis (Jaccard similarity), commitment tracking, and ledger text analysis operate on `content` only. Payload is opaque to governance infrastructure.

**Alternatives:**
- Payload included in governance — watchdog analyzes payload JSON alongside content text. Produces meaningless Jaccard similarity scores on structured data (JSON keys dominate token distribution). Commitment tracking on payload content has no defined semantics.
- Selective governance — governance applies to payload only when a schema is declared. Adds complexity to every governance consumer with unclear benefit.

**Rationale:** Structured data payloads (JSON tool results, API responses, DataParts) are machine-readable content. Text analysis tools (Jaccard similarity, token frequency, semantic overlap) are designed for natural language. Applying them to JSON objects produces noise, not signal. The governance boundary is `content` — natural language that humans and AIs read. `payload` is data that machines process.

If an internal agent moves structured data from `content` to `payload`, the governance gap is intentional: that data was never meaningfully subject to text analysis. The migration exposes a pre-existing gap (structured data in `content` was being analyzed as text, producing noise).

**Trade-offs:** Agents that move structured data from `content` to `payload` lose watchdog visibility over that data. This is correct — the watchdog was never meaningfully analyzing it.

**Depends on:** D4 (payload field)
**Exploration:** surfaced by review (R1-17)
**Status:** captured

## D6: Outbound A2A Delivery Semantics

**Choice:** The outbound A2A backend declares `DeliveryGuarantee.AT_LEAST_ONCE`. Delivery is handled by the existing `DeliveryService` delivery pump — not by `fanOut()` inline virtual threads.

**Alternatives:**
- `BEST_EFFORT` (fire-and-forget from `fanOut()`) — external A2A agents may be slow, unreachable, or rate-limited; fire-and-forget loses messages silently with no retry, no circuit breaking, no catch-up after restarts
- New async outbound queue — unnecessary; the delivery pump IS the async outbound queue (cursor-based, health-tracked, reconciled)

**Rationale:** The delivery pump provides all required capabilities for external service delivery:
- Delivery after transaction commit (no in-transaction HTTP calls)
- Cursor-based catch-up (survives JVM restarts)
- Health-based circuit breaking (unhealthy backends skipped, retried by reconciler)
- Reconciliation on a scheduler (fills gaps from missed signals)

This is the same pattern used by `ConnectorChannelBackend` and `SlackChannelBackend` — both declare `AT_LEAST_ONCE` and make external HTTP calls via the delivery pump.

**Trade-offs:** AT_LEAST_ONCE means the external agent may receive duplicate messages (delivery pump retries on ambiguous failures). External agents must be idempotent — standard A2A expectation.

**Depends on:** D1 (outbound backend architecture)
**Sources:**
- `runtime/src/main/java/io/casehub/qhorus/runtime/gateway/DeliveryService.java` — delivery pump
- `docs/specs/issue-132-delivery-guarantee-backends/2026-06-29-delivery-guarantee-design.md` — delivery guarantee design
- `connector-backend/src/main/java/io/casehub/qhorus/connector/backend/ConnectorChannelBackend.java` — AT_LEAST_ONCE pattern
- `slack-channel/src/main/java/io/casehub/qhorus/slack/SlackChannelBackend.java` — AT_LEAST_ONCE pattern
**Exploration:** surfaced by review (R1-02, R1-20)
**Status:** captured

## D7: Outbound A2A Authentication Model

**Choice:** Outbound A2A HTTP calls use the existing `CredentialResolver` SPI for token resolution. Each `ExternalAgentBinding` stores an `authConfigKey` that the `CredentialResolver` resolves to credentials at call time.

Supported auth types:
- **Bearer token** — API key or static token, resolved via `CredentialResolver`
- **OAuth2 Client Credentials** — machine-to-machine token exchange, managed by a `CredentialResolver` implementation that handles token refresh

TLS: standard HTTPS with certificate validation enabled. No custom trust stores by default.

**Alternatives:**
- Inline token storage (token in ExternalAgentBinding entity) — secrets in the database; poor security posture, no rotation support
- SmallRye Config only — adequate for static tokens but no rotation; `CredentialResolver` abstracts this
- Vault integration — correct for production but `CredentialResolver` SPI already allows a Vault-backed implementation without coupling the binding entity to Vault

**Rationale:** `SlackChannelBackend` already uses `CredentialResolver` for Slack bot token resolution. The same SPI applied to outbound A2A avoids inventing a new credential management pattern. `CredentialResolver` implementations handle rotation, caching, and source (config, vault, environment) — the binding entity only stores the lookup key.

**Trade-offs:** `CredentialResolver` SPI may need review if OAuth2 CC requires parameters beyond a single config key (client ID, token endpoint, scopes). Current SPI shape may need extension for multi-field auth configs.

**Depends on:** D2 (ExternalAgentBinding carries authConfigKey)
**Sources:**
- `slack-channel/src/main/java/io/casehub/qhorus/slack/SlackChannelBackend.java` — existing CredentialResolver usage
**Exploration:** surfaced by review (R1-09)
**Status:** captured
