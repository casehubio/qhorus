# Reputation-Aware Routing — Design Spec

**Issue:** casehubio/qhorus#401
**Date:** 2026-08-25
**Depends on:** casehubio/ledger#200 (materialized trust scores — closed)

---

## Problem

When a COMMAND targets a capability (`target: "role:analyst"`) rather than a specific agent, nothing happens — the `role:analyst` string passes through as a literal. The sending agent must know which specific agent to target, leaking orchestration logic into the agent layer.

The platform already has a full routing infrastructure (`AgentRoutingStrategy` SPI in `casehub-api`, `ComposableAgentRoutingStrategy` in engine, `TrustCandidateClassifier` in ledger). Qhorus doesn't use it.

## Scope

Build a bridge in `MessageService.dispatch()` that resolves `role:X` capability targets to specific agents by delegating to the platform's `AgentRoutingStrategy` SPI.

### What this builds

- `role:` prefix detection in `MessageService.dispatch()`
- Type adapter: `MessageDispatch` + `Instance` → `AgentRoutingContext` + `AgentCandidate`
- Result mapper: `RoutingResult` → resolved target or rejection
- `@DefaultBean` fallback for standalone qhorus (no engine on classpath)
- Per-channel `routingTrustThreshold`
- Routing metadata on `MessageLedgerEntry`
- MCP tools: `set_routing_config`, `get_routing_config`, `get_routing_candidates`

### What this does NOT build

- Custom routing strategies — platform's `ComposableAgentRoutingStrategy` handles all strategy selection
- Custom trust filtering — platform's `TrustCandidateClassifier` handles maturity classification
- Per-channel strategy enum — strategy is a deployment concern, not a per-channel concern

---

## Architecture

### Dispatch pipeline position

Routing sits in `MessageService.dispatch()` after the ACL check (`AllowedWritersPolicy`) and before message persistence:

```
dispatch() receives MessageDispatch with target: "role:analyst"
  → ACL check (AllowedWritersPolicy)
  → ObligorTrustPolicy (skipped — target is role:, not a named agent)
  → NEW: RoutingBridge.resolve(dispatch, channel)
    → detects role: prefix
    → builds AgentRoutingContext from dispatch + channel
    → builds List<AgentCandidate> from InstanceService.findByCapability("analyst")
    → calls AgentRoutingStrategy.select(context, candidates)
    → RoutingResult.Selected → replaces target with resolved instanceId
    → RoutingResult.Unresolvable → rejects dispatch
    → RoutingResult.Escalated → rejects dispatch with escalation reason
  → message persisted with resolved target
  → commitment opens against specific agent
  → ledger entry records routing metadata
```

Non-`role:` targets bypass routing entirely — zero overhead.

### RoutingBridge

New `@ApplicationScoped` bean in `runtime/message/`:

```java
@ApplicationScoped
public class RoutingBridge {

    @Inject AgentRoutingStrategy routingStrategy;
    @Inject InstanceService instanceService;
    @Inject TrustGateService trustGateService;

    public String resolve(MessageDispatch dispatch, Channel channel) {
        String target = dispatch.target();
        if (target == null || !target.startsWith("role:")) {
            return target;  // pass through
        }

        String capability = target.substring("role:".length());
        AgentRoutingContext ctx = buildContext(dispatch, channel, capability);
        List<AgentCandidate> candidates = buildCandidates(capability, channel);

        RoutingResult result = routingStrategy.select(ctx, candidates);

        return switch (result) {
            case RoutingResult.Selected s -> s.single().executorId();
            case RoutingResult.Unresolvable u -> throw new RoutingRejectedException(u.reason());
            case RoutingResult.Escalated e -> throw new RoutingRejectedException(
                    e.escalationReason() + ": " + e.reason());
        };
    }
}
```

### Type adaptation (D8, D9)

**AgentRoutingContext from MessageDispatch:**

| Platform field | Qhorus source | Notes |
|---|---|---|
| `caseId` | `channel.id()` | Channels are qhorus's equivalent of cases |
| `capabilityName` | extracted from `role:X` | The `X` part |
| `caseContext` | `TextNode(dispatch.content())` | Enables `SemanticAgentRoutingStrategy` embedding match when engine-ai is on classpath; null content → null context |
| `tenancyId` | `CurrentPrincipal.tenancyId()` | |
| `experiences` | `List.of()` | CBR experiences are engine-level |
| `cognitiveDemand` | `null` | Engine-level concept |
| `routingSignalWeights` | `null` | Equal weights (platform default) |

**AgentCandidate from Instance:**

| Platform field | Qhorus source | Notes |
|---|---|---|
| `workerId` | `instance.instanceId()` | |
| `capabilities` | `instanceStore.findCapabilities(instance.id())` | |
| `runningJobs` | `0` | No Quartz job tracking in qhorus |
| `health` | `READY` or `UNAVAILABLE` | Based on instance status (online/stale/offline) |
| `agentDescriptor` | `null` | Eidos descriptors are engine-level |
| `matchDegree` | `null` | No eidos matching in standalone qhorus |
| `violations` | `null` | |

The platform routing strategies handle null optional fields gracefully — signal providers skip when data is absent.

### DefaultBean fallback (D7)

When the engine isn't on the classpath (standalone qhorus), no `AgentRoutingStrategy` bean exists. A `@DefaultBean` in qhorus-runtime provides simple highest-trust routing:

```java
@DefaultBean
@ApplicationScoped
public class SimpleHighestTrustRoutingStrategy implements AgentRoutingStrategy {

    @Override
    public RoutingResult select(AgentRoutingContext context, List<AgentCandidate> candidates) {
        if (candidates.isEmpty()) {
            return RoutingResult.unresolvable("no candidates for " + context.capabilityName());
        }
        // Pick candidate with highest trust score from TrustGateService
        // Filter by channel threshold (passed via context)
        // Return highest scorer
    }
}
```

This covers the 90% case. No maturity model, no CBR, no LLM — just "pick the most trusted agent with the right capability."

---

## Data Model

### Channel fields

| Field | Type | Default | Notes |
|---|---|---|---|
| `routingTrustThreshold` | `Double` (nullable) | `null` → global default (0.0) | Passed to routing context; platform classifies candidates |

Config: `casehub.qhorus.routing.default-trust-threshold` in `QhorusConfig` (Optional<Double>, absent = 0.0).

V46 migration: `ALTER TABLE channel ADD COLUMN routing_trust_threshold DOUBLE PRECISION`.

### Ledger metadata

Four nullable columns on `message_ledger_entry` (V2003 migration):

| Column | Type | Notes |
|---|---|---|
| `routing_original_target` | `VARCHAR` | The `role:X` string before resolution |
| `routing_selected_agent` | `VARCHAR` | The instanceId selected |
| `routing_strategy` | `VARCHAR` | Strategy name (from `AgentRoutingStrategy.id()`) |
| `routing_candidate_count` | `INTEGER` | How many candidates passed health filtering |

Populated by `LedgerWriteService.record()` when routing metadata is present on the dispatch.

### RoutingRejectedException

New unchecked exception in `api/message/`. Thrown by `RoutingBridge.resolve()` when routing fails. Caught by `MessageService.dispatch()` and returned as an error — same pattern as `MessageTypeViolationException`.

---

## MCP Tools

Three new tools following existing `snake_case` naming convention:

### `set_routing_config(channel, trust_threshold)`

Sets routing trust threshold on a channel. `trust_threshold` nullable (null clears to global default).

### `get_routing_config(channel)`

Returns current trust threshold for a channel (resolved: channel value or global default).

### `get_routing_candidates(capability, channel?)`

Read-only diagnostic tool. Shows what the router would select without dispatching:
- Lists all agents matching the capability
- Shows each agent's trust score and health status
- Indicates which would pass the threshold
- Shows which the strategy would select

---

## Testing Strategy

### CDI-free unit tests

- `RoutingBridgeTest` — role: prefix detection, non-role: passthrough, Selected/Unresolvable/Escalated mapping, null target passthrough
- `SimpleHighestTrustRoutingStrategyTest` — picks highest score, empty candidates → unresolvable, threshold filtering

### @QuarkusTest integration tests

- `RoutingIntegrationTest` — register agents with capabilities, dispatch COMMAND with `role:X` target, verify resolved target in message store + routing metadata in ledger entry
- `RoutingMcpToolsTest` — set_routing_config, get_routing_config, get_routing_candidates

---

## Files Changed / Created

### New files

| File | What |
|---|---|
| `runtime/.../message/RoutingBridge.java` | Dispatch-time resolution bridge |
| `runtime/.../message/SimpleHighestTrustRoutingStrategy.java` | @DefaultBean fallback |
| `api/.../message/RoutingRejectedException.java` | Unchecked exception for routing failures |
| `runtime/.../config/QhorusRoutingConfig.java` | @ConfigMapping for routing defaults |
| `V46__channel_routing.sql` | Channel routing_trust_threshold column |
| `V2003__ledger_routing_metadata.sql` | Ledger routing columns |

### Modified files

| File | Change |
|---|---|
| `MessageService.java` | Call `RoutingBridge.resolve()` before persist |
| `LedgerWriteService.java` | Record routing metadata on ledger entry |
| `MessageLedgerEntry.java` | Four new nullable fields |
| `MessageLedgerEntryRepository.java` | Queries filtering by routing fields (optional) |
| `Channel.java` / `ChannelEntity.java` | `routingTrustThreshold` field |
| `ChannelCreateRequest.java` | Optional `routingTrustThreshold` in builder |
| `QhorusMcpTools.java` | Three new @Tool methods |
| `ChannelResource.java` | PUT routing config endpoint |
| `CLAUDE.md` | Document routing bridge |

---

## References

- `io.casehub.api.spi.routing.AgentRoutingStrategy` — platform routing SPI
- `io.casehub.engine.internal.routing.ComposableAgentRoutingStrategy` — default strategy
- `io.casehub.ledger.routing.TrustCandidateClassifier` — trust maturity model
- `docs/platform/routing.md` — four-layer routing architecture
- Garden GE-20260616-17187e — TrustGateService delegation chain
- Garden GE-20260804-565c2c — ActorTrustScoreRepository cross-PU lookup
- `docs/roadmap-epics-2026.md` — E4 epic definition
