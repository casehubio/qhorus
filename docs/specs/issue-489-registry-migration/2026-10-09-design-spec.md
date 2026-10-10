# Modular Fleet Architecture — Claudony as Composable LLM Ops Layer

**Issue:** casehubio/claudony#267
**Date:** 2026-10-09
**Status:** Design

---

## Problem

Claudony started as a personal remote terminal tool for running Claude Code sessions on a Mac Mini. It grew into an LLM fleet manager with pools, scaling, mesh embedding, and CaseHub SPI implementations. Now it needs to serve a third role: the composable LLM ops layer that applications deployed by scaffold can consume.

The current architecture doesn't support this:

- **Pools are node-local.** `AgentSessionManager` talks to local tmux. `PeerRegistry` knows about peers but not their pools. No cross-node visibility.
- **Registries are fragmented.** Qhorus has `InstanceRegistry`, Claudony has `PeerRegistry` and `AgentPoolDefinitionRegistry`, `PoolMeshRegistrar` bridges between them. Four registration mechanisms with four heartbeat models.
- **No application concept.** Deployed apps can't register with Claudony to request LLM capacity or mesh channels.
- **No composable UI.** The dashboard is a standalone app, not panels that scaffold or trellis can compose.

## Insight

LLM agents blur the line between infrastructure and participants. Traditional ops tools provision and observe black-box services. Traditional interaction tools let you communicate with agents. Claudony does both — it provisions pools of LLM agents (infrastructure), watches them coordinate on a mesh (observability), reads their conversations (transparency), and lets humans interject (participation). The provisioning world and the interaction world collapse into one because the "service" is a thinking entity you can communicate with.

No existing tool or standard vocabulary covers this. Service catalogs (Backstage) track metadata. Service meshes (Istio/Consul) observe traffic. Neither lets you participate in what the services are doing. Claudony is all three — catalog, mesh, and interaction layer — because LLM agents require all three.

## Architecture

### Claudony as Three Things

Claudony is a personal tool, a set of platform services, and a component library. Each consumer assembles what it needs:

| Consumer | Terminal | Fleet | Mesh | UI Panels | Registry |
|----------|----------|-------|------|-----------|----------|
| **Standalone app** (personal) | Yes | Yes | Yes | Yes | In-memory |
| **Scaffold Ops Perspective** | No | Yes (API) | Yes (API) | Yes | Ops-tier JPA |
| **Trellis** (desktop) | Yes | — | — | Selective | — |
| **Any CaseHub app** | No | Registration API | Registration API | — | Client only |

The standalone app is unchanged — it remains the all-in-one personal tool. Scaffold and other consumers access fleet and mesh capabilities as independent services via GraphQL, with UI panels loaded dynamically based on what backends are available.

### Integration Model

Runtime-composable independent services. No build-time coupling between scaffold and Claudony.

- Each Claudony capability (fleet, mesh) is an independently deployable service
- Services expose GraphQL APIs (the platform has `graphql-generator` and `graphql-spring-generator` for auto-generation from module SPIs)
- UI panels are `blocks-ui` web components, lazy-loaded by the shell when the corresponding backend responds
- Service discovery via the registration service tells the UI shell what backends are available

This means scaffold never needs Claudony as a Maven dependency. It discovers running Claudony services at runtime and composes their UI panels dynamically.

### The Registration Service

The foundation for everything else. A unified SPI in `platform-api` that all CaseHub components use for registration, discovery, and lifecycle management.

#### Starting Point: EndpointRegistry

`EndpointRegistry` already exists in `platform-api` with:
- Register/resolve/discover/deregister
- Tenant-scoped with platform-global fallback
- CDI event (`EndpointRegistered`) fired on registration
- Three backends: NoOp (`@DefaultBean`), InMemory (`@Alternative @Priority(100)`), JPA
- Protocol discrimination (`EndpointProtocol`: HTTP, GRPC, KAFKA, AMQP, MCP, CAMEL, QHORUS)

Stage 1 evaluates whether `EndpointRegistry`'s key model `(Path, tenancyId)` can accommodate the broader entity types (services, pools, channels, apps, nodes) or whether a sibling SPI with richer semantics is needed.

#### What the Registry Adds Beyond Endpoints

| Capability | EndpointRegistry | Registry SPI |
|-----------|-----------------|-------------|
| Register/deregister | Yes | Yes |
| Tenant scoping | Yes | Yes |
| CDI events | Fire-and-forget | Watch/subscribe with filters |
| Heartbeat / TTL | No | Yes |
| Health status | No | Yes (healthy/degraded/down) |
| Typed entities | All EndpointDescriptor | service, pool, channel, app, node |
| Relationships | No | Yes (owns, runs-on, serves, consumes) |
| Cascade rules | No | Yes (deregister app → drain pools) |

#### Entity Model

```java
sealed interface RegistryEntry {
    String id();
    String type();          // "service", "pool", "channel", "app", "node"
    String namespace();     // application namespace, or "default"
    String tenancyId();
    Map<String, String> metadata();
    Instant registeredAt();
    Instant lastHeartbeat();
    Duration ttl();
    HealthStatus health();  // HEALTHY, DEGRADED, DOWN
}

record Relationship(
    String sourceId,
    String targetId,
    String type             // "owns", "runs-on", "serves", "consumes"
) {}
```

#### SPI Contract

```java
interface RegistryService {
    // Entity lifecycle
    void register(RegistryEntry entry);
    void heartbeat(String id);
    void deregister(String id);

    // Query
    Optional<RegistryEntry> resolve(String id);
    List<RegistryEntry> discover(RegistryQuery query);

    // Relationships
    void link(Relationship rel);
    void unlink(String sourceId, String targetId);
    List<Relationship> relationships(String id);

    // Reactive
    void watch(RegistryQuery query, Consumer<RegistryEvent> listener);
}
```

#### Implementations

| Implementation | Module | Priority | Capabilities |
|---------------|--------|----------|-------------|
| NoOp | `platform-api` | `@DefaultBean` | No-ops everything, apps without registry degrade gracefully |
| InMemory | `platform-endpoints-memory` (or new) | `@Alternative @Priority(50)` | Local registration, heartbeat, relationships. No durability. For standalone Claudony. |
| JPA | `casehub-ops` | `@Alternative @Priority(100)` | Full durable implementation with cascade support. Deployed with scaffold. |

### Application Namespace Model

Applications register with the registry and receive a namespace. The namespace provides organisational grouping for the ops view and channel scoping.

**Capacity model:** Pools provide shared capacity — multiple apps draw from the same capacity budget, but each app gets its own sessions. LLM sessions carry state (conversation context, working directory, environment) and cannot be shared across applications. "Shared pool" means shared capacity allocation, not shared sessions.

**Alternative under evaluation:** Per-app pools with a global capacity ceiling. Each app owns its pools (isolation), a platform-wide capacity controller prevents the total from exceeding fleet capacity. Simpler than quota-based allocation — no exhaustion policy needed. The existing `agent-gate` concurrency limiter provides a reference pattern. Decision deferred to Stage 3 implementation.

**Registration flow:**
1. Scaffold deploys an app
2. App registers with the registry: `type="app"`, `namespace="code-review"`, metadata includes GAV, deployment node
3. App requests fleet resources: pools register with `namespace="code-review"`, linked to the app via `owns` relationship
4. Channels created with namespace prefix: `code-review/reviews`, `code-review/findings`
5. Ops view queries the registry for the full topology graph

**Undeploy flow:**
1. Scaffold undeploys the app
2. App deregisters from the registry
3. Cascade: drain pools, close channels, clean up sessions
4. Durable delivery ensures cascade completes even if downstream services are temporarily down

### Claudony Module Decomposition

Current structure bundles concerns:
- `claudony-core` — session management, tenant context, expiry (terminal-specific)
- `claudony-casehub` — CaseHub SPIs, pool management, worker provisioning (fleet + casehub mixed)
- `claudony-app` — fleet, auth, MCP, mesh, channel event bus, case event broadcaster (everything)

Target decomposition for composability:

| Module | Contains | Depends on |
|--------|----------|-----------|
| `claudony-fleet` | Pool management, scaling, eviction, fleet scripts, `AgentSessionManager` | platform registry SPI |
| `claudony-mesh` | Qhorus embedding, mesh management, channel event bus | platform registry SPI, Qhorus |
| `claudony-terminal` | tmux session management, WebSocket streaming, `TmuxService`, `SessionRegistry` | — |
| `claudony-casehub` | CaseHub SPI implementations (provisioner, channel provider, context provider, status listener) | claudony-fleet, claudony-mesh, claudony-terminal |
| `claudony-app` | Standalone Quarkus application, auth, REST resources, MCP endpoint | all of the above |
| `claudony-ui-fleet` | blocks-ui fleet/pool/mesh panels (extracted from webui) | — (frontend package) |

**Key dependency to resolve:** `PoolMeshRegistrar` bridges fleet → mesh (pool lifecycle → Qhorus instance registration). With the unified registry, this bridge becomes relationship links rather than direct coupling — but the fleet module still needs to know about mesh concepts. This may mean fleet and mesh share a thin API module, or the relationship is managed purely through the registry.

### UI Composition

The existing panels (`claudony-fleet-panel`, `claudony-pool-panel`, `claudony-mesh-panel`) are already self-contained Lit web components. The extraction path:

1. Extract panels into `@casehubio/blocks-ui-fleet` (or similar npm package)
2. Panels talk to backend APIs (REST/GraphQL, SSE for real-time)
3. Any shell (scaffold ops perspective, standalone Claudony, trellis) imports and composes them
4. Runtime discovery: the shell queries the registry for available backends and loads matching panels

The `casehub-pages` composition model (`registerPanel`, `hostPanel`, `tabs`) works unchanged — the panels just come from a shared package instead of being bundled in the app.

## Staged Delivery

### Stage 1 — Registry Core (platform)

**Scope:** New SPI in `casehub-platform-api`. Evaluate `EndpointRegistry` extensibility. Implement heartbeat scheduler, TTL expiry, CDI events on state transitions, relationship links.

**Deliverables:**
- `RegistryService` SPI in `platform-api` (or extension of `EndpointRegistry`)
- `RegistryEntry`, `Relationship`, `RegistryQuery`, `RegistryEvent` types
- Heartbeat/TTL scheduler
- InMemory implementation with tests
- JPA implementation for ops-tier

**Cross-repo:** casehub-platform only.

### Stage 2 — Migration (qhorus, claudony)

**Scope:** Replace existing registries one at a time. Strangler fig pattern — old registries stay operational until each consumer migrates. Each migration is a standalone PR.

| Current registry | Migration |
|-----------------|-----------|
| Qhorus `InstanceRegistry` | Wrap `RegistryService` with `type="agent-instance"` |
| Claudony `PeerRegistry` | Wrap `RegistryService` with `type="node"` + heartbeat |
| `AgentPoolDefinitionRegistry` | Wrap `RegistryService` with `type="pool"` |
| `PoolMeshRegistrar` | Replace bridge code with `Relationship` links |

**Cross-repo:** casehub-platform, qhorus, claudony.

### Stage 3 — App/Topology Layer (ops, claudony, scaffold)

**Scope:** Application registration, namespace allocation, topology links, ops view.

**Deliverables:**
- `type="app"` entity in registry, registered by scaffold on deploy
- `type="fleet"` entity, linking apps to pool groups
- Relationship graph: app → fleet → pool → node, channel → app
- Allocation model decision: shared capacity vs per-app pools with ceiling
- Claudony module decomposition (fleet/mesh/terminal split)
- `@casehubio/blocks-ui-fleet` package extraction
- Scaffold ops perspective composing fleet/mesh panels
- GraphQL API surface for fleet and mesh services

**Cross-repo:** casehub-platform, casehub-ops, claudony, scaffold.

### Stage 4 — Durable Lifecycle

**Scope:** Reliable cascade on deregistration. Open design question — evaluate existing platform capabilities.

**Candidates to evaluate:**
1. Durable notifications with acceptance — designed for human notifications, may need adaptation for system-to-system lifecycle events
2. Work items as cleanup tasks — task-oriented, may not have the right triggering/subscription model
3. Qhorus commitment store — conversation commitments, not infrastructure lifecycle
4. Purpose-built transactional outbox — if none of the above fit

**Deliverables:**
- Evaluation document comparing candidates against requirements (reliability, ordering, exactly-once, timeout/retry)
- Implementation of chosen mechanism
- Cascade rules: app deregister → drain pools → close channels → clean sessions
- Integration tests proving eventual consistency under service failure

**Cross-repo:** casehub-platform, potentially qhorus.

## What This Does NOT Cover

- **Terminal/session/case interaction** — stays as-is. The workbench, channel panels, worker panels are unchanged.
- **Cross-node pool distribution** — acquiring sessions on remote nodes. This is a follow-on that builds on the registry (the registry knows what pools exist where; the fleet service decides where to place sessions).
- **Qhorus federation** — distributing Qhorus across multiple instances/databases. Currently single-instance, stays that way. The registry provides visibility; federation is a separate concern.
- **Chat-app absorption** — the chat-app's rich UI becoming Claudony's mesh view. The `blocks-ui-fleet` extraction enables this, but the actual absorption is a separate design.

## Open Questions

1. **Extend EndpointRegistry or sibling SPI?** Stage 1's first task. Depends on whether `(Path, tenancyId)` is a viable key model for the broader entity types.
2. **Shared capacity vs per-app pools?** Stage 3 allocation model decision. The registry supports either; the fleet service implements the policy.
3. **Stage 4 delivery mechanism?** Which existing platform capability (if any) works for durable lifecycle cascades, or does it need something new?
4. **GraphQL schema federation?** If multiple Claudony services each expose their own GraphQL schema, how does the shell federate them into one query surface?

## References

- `EndpointRegistry` in `platform-api` — existing registration SPI, starting point for evaluation
- `BackendInstanceRegistry` in `platform-agent-api` — existing agent backend registry
- Qhorus `InstanceService` — agent mesh instance registration
- Claudony `PeerRegistry` — fleet peer node discovery
- `AgentPoolDefinitionRegistry` — pool definition storage
- `PoolMeshRegistrar` — pool→mesh lifecycle bridge
- `agent-gate` — existing concurrency/capacity control pattern
- scaffold#52 — Ops Perspective vision
- scaffold#39 — generic CaseHub web UI
- claudony#246 — declarative LLM fleet deployment
- claudony#210 — cross-machine agent pool distribution
- claudony#208 — ops perspective integration
- Consul service registry model — design reference for heartbeat/TTL/health
- SmallRye Stork — Quarkus-native service discovery (potential client-side integration)
- `graphql-generator` / `graphql-spring-generator` in platform — GraphQL auto-generation
