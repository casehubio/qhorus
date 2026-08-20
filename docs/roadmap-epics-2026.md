# Qhorus Roadmap — Epic Breakdown & Critical Path

**Date:** 2026-08-20
**Prioritization:** Low cost + high impact first. "Wow factor" = demo-able, immediately visible, makes people say "I want that."

---

## Cost/Impact Assessment

| # | Capability | Cost | Impact | Wow | Rationale |
|---|---|---|---|---|---|
| E1 | Cross-channel causal graphs | S | Very high | ***** | Data already in ledger. Just need the query layer. "Show me the full chain that caused this failure" is a one-slide demo. |
| E2 | Cascade containment | S | High | ***** | Connect existing watchdog alerts to existing pause/deregister. "The system auto-quarantined the agent" is visceral. |
| E3 | Active governance policies | S | High | *** | Add enforcement mode to existing protocol evaluation. Practical, not flashy — but unlocks E2 and E5. |
| E4 | Reputation-aware routing | M | Very high | ***** | Use existing trust scores for routing. "Work automatically goes to the most trusted agent" is a headline feature. |
| E5 | Compliance evidence export | M | High | *** | Package existing ledger data as reports. EU AI Act deadline creates urgency. Not flashy but commercially important. |
| E6 | Signed Agent Cards | M-L | Medium | ** | Crypto anchoring. Ecosystem credibility. Important but not demo-able in 30 seconds. |
| E7 | Formal verification | M | Medium | ** | Property specification layer. Intellectually significant. Hard to demo. |
| E8 | Context-aware redistribution | L | Very high | ***** | "Agent ran out of context, system auto-delegated to a backup." Incredible demo. But depends on E4. |
| E9 | A2A push notifications | M | Medium | ** | Ecosystem completeness. No standalone wow. |

---

## Epics — Ordered by Execution Priority

### Epic 1: Cross-Channel Causal Graphs

**Scale:** S | **Complexity:** Low-Med | **Wow:** "Show me why this failed — across every channel it touched."

The data is already in the ledger. Every message has `causedByEntryId` and `correlationId`. External delegations (via A2A outbound bridge) create ledger entries with the same correlation chain. The missing piece is a query that walks these links across channel boundaries and renders the result.

**Deliverables:**
- `get_causal_graph(correlationId)` MCP tool — returns the full cross-channel tree: who asked whom, what each answered, how long each step took
- `get_attribution_chain(entryId)` MCP tool — walks causedByEntryId backward to root, crossing channels
- Visual rendering as a directed graph (channel-colored nodes, message-type edges)
- REST endpoint for external consumption

**Why first:** Zero new infrastructure. Pure query layer over existing data. Immediately useful for debugging multi-agent failures. Makes every other feature more valuable because you can see what's happening.

---

### Epic 2: Cascade Containment

**Scale:** S | **Complexity:** Low | **Wow:** "A loop started. The system caught it and quarantined the agent in 200ms."

Connect existing detection (watchdog conditions) to existing containment (channel pause, backend deregister, notification bridge). The watchdog fires alerts today but takes no action. Adding an action policy per watchdog turns detection into containment.

**Deliverables:**
- `WatchdogAction` enum: `ALERT` (current), `PAUSE_CHANNEL`, `DEREGISTER_AGENT`, `QUARANTINE` (pause + deregister + notify)
- Per-watchdog action configuration (persisted, MCP-configurable)
- Automatic execution: watchdog fires → action policy → containment
- `CommitmentExpiredEvent` fires for affected obligations
- Containment audit trail in ledger (EVENT with containment telemetry)

**Why second:** Builds on existing watchdog + existing pause/deregister + existing notification bridge. No new abstractions. The Galileo AI cascade research makes this immediately relevant — "87% poisoned in 4 hours" is the problem statement; this is the answer.

---

### Epic 3: Active Governance Policies

**Scale:** S | **Complexity:** Low | **Wow:** "This channel blocks unauthorized delegations. Protocol violations are rejected, not just logged."

Protocol enforcement is currently advisory. Add an enforcement mode per channel:

**Deliverables:**
- `Channel.enforcementMode` field: `ADVISORY` (current default), `BLOCKING`, `QUARANTINE`
- `MessageService.dispatch()` respects enforcement mode — BLOCKING returns error, QUARANTINE pauses after threshold
- `set_enforcement_mode(channel, mode)` MCP tool
- `DispatchResult` gains `blocked: boolean` + `blockReason: String` for BLOCKING mode
- REST endpoint `PUT /api/channels/{id}/enforcement-mode`

**Why third:** Enables E2 (cascade containment uses quarantine mode). Enables E5 (compliance needs enforcement evidence). Low cost because protocol evaluation already runs at dispatch time — only the response to violations changes.

---

### Epic 4: Reputation-Aware Routing

**Scale:** M | **Complexity:** Med | **Wow:** "Three agents can do this. The system picked the one with a 94% fulfillment rate."

When a COMMAND targets a capability (`target: "role:analyst"`) rather than a specific agent, the system currently returns all matching instances. Add trust-score-weighted selection.

**Deliverables:**
- `CapabilityRouter` — resolves capability targets to the best-qualified agent using trust scores
- Routing strategies: `HIGHEST_TRUST` (default), `ROUND_ROBIN_WEIGHTED`, `RANDOM_WEIGHTED`
- `routing_strategy` per channel (MCP-configurable)
- Trust score threshold — agents below threshold excluded from routing
- Routing decision recorded in ledger (why this agent was selected)
- `get_routing_candidates(capability)` MCP tool — shows ranked candidates with scores

**Why fourth:** Depends on existing trust scoring (already built). Makes trust actionable — the most important step in closing the trust loop. RepuNet showed cooperation climbs from 20% to 85% with reputation. This is the headline feature for the trust story.

---

### Epic 5: Compliance Evidence Export

**Scale:** M | **Complexity:** Med | **Wow:** "Here's your EU AI Act audit report. Auto-generated from the ledger."

Package existing ledger data into compliance-ready formats. The data exists — this is a presentation and export layer.

**Deliverables:**
- Attribution chain report — full delegation tree per correlation, with timestamps and outcomes
- Obligation fulfillment report — per-agent and per-channel fulfillment rates, stale obligation counts
- Trust score history export — per-actor score trajectories with attestation evidence
- Protocol violation log — enforcement actions, advisory violations, blocked messages
- PROV-DM export (extends existing example)
- JSON, CSV, and PDF export formats
- REST endpoints for on-demand report generation
- Scheduled report generation (weekly/monthly compliance digest)

**Why fifth:** EU AI Act enforcement is August 2, 2026. 71% of enterprises lack governance frameworks. Qhorus already has the data — this packages it for the people who write the cheques. Depends on E1 (causal graphs power the attribution reports) and E3 (enforcement logs power the violation reports).

---

### Epic 6: Signed Agent Cards + DID

**Scale:** M-L | **Complexity:** High | **Wow:** "Every agent's identity is cryptographically verified."

Cryptographic identity verification for agent cards. Moves trust from configuration-based to verifiable.

**Deliverables:**
- Agent card signing (Ed25519 or similar)
- Signature verification on inbound A2A agent card fetch
- Optional DID anchoring (W3C DID standard)
- Verifiable credential issuance for agent capabilities
- `AgentCardResource` enhanced with signature headers
- Trust score bonus for cryptographically verified agents

**Why sixth:** Important for ecosystem credibility and external integrations. Higher cost than E1-E5. Not demo-able in 30 seconds — it's infrastructure that makes other things more trustworthy.

---

### Epic 7: Formal Verification

**Scale:** M | **Complexity:** High (intellectual) | **Wow:** "We proved the commitment lifecycle has no deadlocks."

Encode the commitment state machine as temporal logic properties and verify them.

**Deliverables:**
- Property specification language (subset of CTL/LTL)
- 15-20 temporal logic invariants covering liveness, safety, fairness, deadlock freedom
- Offline verification tool — runs against ledger history
- Runtime monitoring mode — checks invariants at dispatch time
- Violation report when properties are broken

**Why seventh:** Intellectually significant, hard to demo. The proof is valuable for trust and academic credibility but doesn't produce visible features. Depends on stable commitment lifecycle (already stable).

---

### Epic 8: Context-Aware Work Redistribution

**Scale:** L | **Complexity:** High | **Wow:** "The agent's context filled up. The system moved its work to a backup — automatically."

Automatic obligation redistribution when context pressure exceeds threshold.

**Deliverables:**
- `CONTEXT_PRESSURE` watchdog triggers automatic HANDOFF (not just alert)
- Delegate selection via reputation-aware routing (E4)
- Channel summary compression before redistribution attempt
- Redistribution recorded in ledger with full audit trail
- Configurable pressure threshold per agent/channel
- Grace period — agent gets a window to compress context before redistribution fires

**Why eighth:** Highest wow factor of any feature. But depends on E4 (routing) and E2 (containment pattern). The cost is high because automatic delegation requires careful handling of in-flight commitments, partial work transfer, and context handoff. Save for when the foundation is solid.

---

### Epic 9: A2A Push Notifications

**Scale:** M | **Complexity:** Med | **Wow:** "External agents get notified of channel events in real-time."

Bidirectional A2A participation — not just request/response but push.

**Deliverables:**
- A2A-native push notification endpoint on external agent cards
- `PushNotificationBackend` — MessageObserver that pushes to registered external agents
- Registration via `ExternalAgentBinding` push endpoint field
- Retry + health tracking (reuse delivery pump pattern)
- Push filtering — which message types/channels trigger pushes

**Why ninth:** Ecosystem completeness feature. No standalone wow. Depends on A2A outbound (done) and webhook-observer patterns (done).

---

## Critical Path

```
         ┌──────────┐     ┌──────────┐     ┌──────────┐
    ┌───>│ E1 Causal │────>│          │────>│ E5       │
    │    │ Graphs    │     │          │     │ Compliance│
    │    └──────────┘     │          │     └──────────┘
    │                      │          │
────┤    ┌──────────┐     │ E4       │     ┌──────────┐
NOW │───>│ E2       │────>│ Routing  │────>│ E8       │
    │    │ Containmt│     │          │     │ Context  │
    │    └──────────┘     │          │     │ Redist   │
    │                      │          │     └──────────┘
    │    ┌──────────┐     │          │
    └───>│ E3 Active│────>│          │     ┌──────────┐
         │ Governnce│     └──────────┘     │ E6 Signed│
         └──────────┘                      │ Cards    │
                                           └──────────┘
                           ┌──────────┐
                           │ E7 Formal│    ┌──────────┐
                           │ Verify   │    │ E9 A2A   │
                           └──────────┘    │ Push     │
                                           └──────────┘
```

**Phase 1 (parallel, S each):** E1 + E2 + E3 — three independent S-sized epics that can run simultaneously. Total elapsed: ~2-3 weeks. All build on existing infrastructure with no new abstractions.

**Phase 2 (M):** E4 — reputation-aware routing. Depends on trust scores (exist) and benefits from E3 (enforcement mode). The headline feature.

**Phase 3 (parallel, M each):** E5 + E6 — compliance export (benefits from E1 causal + E3 enforcement) and signed cards (independent). Can run in parallel.

**Phase 4 (independent, M each):** E7 + E9 — formal verification and A2A push. No dependencies on Phase 2/3. Can start whenever capacity allows.

**Phase 5 (L):** E8 — context-aware redistribution. Depends on E4 (routing) and E2 (containment pattern). The crown jewel, saved for when the foundation is solid.

---

## Summary — What to build first

The first three epics (causal graphs, cascade containment, active governance) are all S-sized, can run in parallel, and produce immediately visible results:

1. "Show me why this failed" (E1) — cross-channel attribution
2. "The system caught it" (E2) — automatic containment
3. "This channel enforces policy" (E3) — blocking violations

Together they transform qhorus from "governance that records" to "governance that acts." That's the step change.

Then E4 (routing) closes the trust loop — the system routes work to agents it trusts. That's the wow.

Then E5 (compliance) makes it commercially relevant — "here's your EU AI Act report." That's the money.

Everything after that is ecosystem depth and intellectual credibility — valuable but not urgent.
