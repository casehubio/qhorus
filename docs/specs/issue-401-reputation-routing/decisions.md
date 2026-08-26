# Decisions — #401 Reputation-Aware Routing (Revised)

**Revision note:** Original decisions D1-D6 revised after platform audit revealed
the full `AgentRoutingStrategy` SPI chain in `casehub-api`, `casehub-engine`, and
`casehub-ledger`. Qhorus bridges to the existing platform routing — it does not
build its own.

## D1: Bridge to platform AgentRoutingStrategy at dispatch time (REVISED)

**Choice:** `MessageService.dispatch()` detects `role:` capability targets and delegates to the platform's `AgentRoutingStrategy.select()` SPI. The message is stored with the resolved instanceId.
**Original:** Custom `CapabilityRouter` with its own strategy implementations.
**Revision:** No custom router. Qhorus constructs `AgentRoutingContext` + `List<AgentCandidate>` from its own types and calls the platform SPI. The platform's `ComposableAgentRoutingStrategy` (@DefaultBean in engine) handles trust scoring, semantic matching, LLM-based routing, and CBR — depending on what's on the classpath.
**Rationale:** The platform already has a four-layer routing architecture (score computation → policy → classical strategy → AI strategy). Building a parallel system in qhorus duplicates infrastructure and diverges from the platform's trust maturity model.
**Trade-offs:** Qhorus gains a compile dependency on `casehub-api` routing SPIs (already a transitive dep via `casehub-platform-api`). When the engine is NOT on the classpath (standalone qhorus), a `@DefaultBean` fallback in qhorus provides simple highest-trust routing.
**Sources:** `io.casehub.api.spi.routing.AgentRoutingStrategy`, `ComposableAgentRoutingStrategy`, `DefaultWorkOrchestrator.doSubmit()`, `docs/platform/routing.md`
**Exploration:** quick → deep (revised after platform audit)
**Status:** captured

## D2: Map RoutingResult to dispatch outcomes (REVISED)

**Choice:** `RoutingResult.Selected` → resolve target to `assignment.single().executorId()`. `RoutingResult.Unresolvable` → reject dispatch with error. `RoutingResult.Escalated` → reject dispatch with escalation reason.
**Original:** Custom `NoTrustedAgentException`.
**Revision:** Use the platform's sealed result type directly. No custom exceptions — the `RoutingResult` variants carry the reason.
**Rationale:** The platform's three-variant sealed interface covers all cases. `Escalated` provides richer context than a simple rejection (includes `EscalationReason` enum and rationale string).
**Trade-offs:** None — strictly better than custom exceptions.
**Sources:** `io.casehub.api.spi.routing.RoutingResult`
**Exploration:** quick
**Status:** captured

## D3: Routing metadata on existing ledger entry (UNCHANGED)

**Choice:** Add routing fields to `MessageLedgerEntry`: routingOriginalTarget, routingSelectedAgent, routingStrategy, routingCandidateCount.
**Rationale:** Lightweight and queryable. Routing enriches the dispatch decision.
**Trade-offs:** V2003 migration on joined subclass table.
**Sources:** `LedgerWriteService.record()`, `MessageLedgerEntry`
**Exploration:** quick
**Status:** captured

## D4: Per-channel threshold feeds TrustCandidateClassifier (REVISED)

**Choice:** `Channel.routingTrustThreshold` (nullable, global default 0.0) is passed as context to the platform routing SPI via `AgentRoutingContext`. The platform's `TrustCandidateClassifier` uses it for QUALIFIED/EXCLUDED classification.
**Original:** Custom threshold filtering in a `CapabilityRouter`.
**Revision:** The threshold is a routing parameter, not a filter step. It flows into the `AgentRoutingContext` and the platform's trust maturity model handles classification (BOOTSTRAP/BORDERLINE/QUALIFIED/EXCLUDED_PHASE2B/EXCLUDED_PHASE3).
**Rationale:** The platform's four-phase maturity model is richer than a simple threshold filter. A new agent with <10 samples gets BOOTSTRAP classification, not silently excluded.
**Trade-offs:** The threshold semantics are defined by the platform, not qhorus. Qhorus cannot override the classification logic — only provide the threshold value.
**Sources:** `TrustCandidateClassifier`, `docs/platform/routing.md` Layer 2
**Exploration:** quick → deep (revised after platform audit)
**Status:** captured

## D5: No custom strategy enum — platform owns strategy selection (NEW)

**Choice:** Remove the proposed `RoutingStrategy` enum (HIGHEST_TRUST/ROUND_ROBIN/RANDOM). Strategy selection is platform-level via `@Priority`-ordered `AgentRoutingStrategy` CDI beans and `ComposableAgentRoutingStrategy`'s signal blending.
**Alternatives:**
- Per-channel strategy enum in qhorus — duplicates platform strategy selection
- Qhorus-only strategies as fallback — adds maintenance burden for a parallel system
**Rationale:** The platform's composable strategy blends trust scores, semantic similarity, LLM reasoning, and CBR evidence with configurable weights. A qhorus enum with three fixed strategies is a subset that would diverge over time.
**Trade-offs:** Qhorus channels cannot override the platform's strategy selection per-channel. The strategy is a deployment-level concern (which `@Alternative @Priority` beans are on the classpath), not a per-channel concern. This is consistent with how the engine uses it.
**Sources:** `ComposableAgentRoutingStrategy`, `RoutingSignalAssembler`
**Exploration:** deep (platform audit)
**Status:** captured

## D6: Prefix convention for capability targets (UNCHANGED)

**Choice:** Targets starting with `role:` trigger routing. All other targets are specific instanceIds.
**Rationale:** Matches existing convention (HANDOFF, AllowedWritersPolicy synthetic tags).
**Sources:** CLAUDE.md, `AllowedWritersPolicy`
**Exploration:** quick
**Status:** captured

## D7: DefaultBean fallback for standalone qhorus (NEW)

**Choice:** When `AgentRoutingStrategy` is not on the classpath (standalone qhorus without the engine), a `@DefaultBean` implementation in qhorus-runtime provides simple highest-trust-score routing using `TrustGateService.currentScore()`.
**Alternatives:**
- Require engine on classpath for routing — breaks standalone qhorus installations
- No routing without engine — capability targets pass through unresolved
**Rationale:** Standalone qhorus (embedded in Claudony without the full engine) should still resolve `role:` targets. The fallback is simple: query instances by capability, score each via `TrustGateService`, pick the highest. No signal blending, no maturity model, no CBR.
**Trade-offs:** The fallback is a thin subset of the platform routing. It doesn't handle BOOTSTRAP classification, semantic matching, or LLM-based selection. But it covers the 90% case (pick the most trusted agent with the right capability).
**Depends on:** D1 (SPI bridge pattern)
**Sources:** `TrustGateService.currentScore()`, garden GE-20260616-17187e (TrustGateService delegation)
**Exploration:** quick
**Status:** captured

## D8: AgentRoutingContext construction from MessageDispatch (NEW)

**Choice:** Build `AgentRoutingContext` from qhorus types:
- `caseId` → `channelId` (UUID — channels are the qhorus equivalent of cases)
- `capabilityName` → extracted from `role:X` target (the `X` part)
- `caseContext` → `null` (qhorus dispatches don't carry case context JSON; engine enriches this)
- `tenancyId` → from `CurrentPrincipal.tenancyId()`
- `experiences` → `List.of()` (CBR experiences are engine-level)
- `cognitiveDemand` → `null` (engine-level concept)
- `routingSignalWeights` → from channel config or `null` (equal weights)
**Rationale:** Most `AgentRoutingContext` fields are engine-level concerns that qhorus doesn't have. The bridge provides the minimum: channel identity, capability name, tenancy. The platform routing SPI handles gracefully when optional fields are null — `ComposableAgentRoutingStrategy` skips signal providers that return no data.
**Sources:** `AgentRoutingContext` record definition, `ComposableAgentRoutingStrategy.select()`
**Exploration:** quick
**Status:** captured

## D9: AgentCandidate construction from Instance (NEW)

**Choice:** Build `AgentCandidate` from qhorus `Instance` + `InstanceService`:
- `workerId` → `instance.instanceId()`
- `capabilities` → `instanceStore.findCapabilities(instance.id())`
- `runningJobs` → `0` (qhorus has no Quartz job tracking)
- `health` → `AgentHealth.READY` (default; `UNAVAILABLE` if instance status is "stale" or "offline")
- `agentDescriptor` → `null` (eidos descriptors are engine-level)
- `matchDegree` → `null` (no eidos matching in standalone qhorus)
- `violations` → `null`
**Rationale:** `AgentCandidate` is engine-centric (Quartz jobs, eidos descriptors, behavioral violations). Qhorus populates what it can (identity, capabilities, health) and leaves engine-specific fields null. The routing strategies handle nulls gracefully.
**Sources:** `AgentCandidate` record definition, `AgentCandidateFactory` in engine
**Exploration:** quick
**Status:** captured
