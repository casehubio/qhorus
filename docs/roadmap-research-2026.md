# Qhorus Roadmap — Research-Informed Strategic Direction

**Date:** 2026-08-20
**Context:** Post-#396 (A2A interop bridge complete). Research survey across agent governance, A2A protocol evolution, formal verification, and trust systems.

---

## Current Position

Qhorus occupies a distinctive position in the multi-agent coordination landscape: it started from governance and added interoperability, while most frameworks are racing to add governance after the fact. The research literature in 2025-2026 is converging on exactly the problems qhorus already solves.

### What qhorus has that the field is asking for

| Capability | Qhorus implementation | Industry status |
|---|---|---|
| Interaction logging | Normative audit ledger with SHA-256 tamper evidence | Identified as requirement by [Coordination Transparency (Springer, 2026)](https://link.springer.com/article/10.1007/s00146-026-02853-w) |
| Obligation lifecycle | 10-type speech act taxonomy + commitment state machine (OPEN -> FULFILLED/DECLINED/FAILED/DELEGATED/EXPIRED) | [XMPro MAGS](https://github.com/XMPro/Multi-Agent/blob/main/docs/concepts/deontic-principles.md) and [deontic logic research (arXiv, 2025)](https://arxiv.org/abs/2501.05765) are building toward this |
| Runtime policy enforcement | Dispatch pipeline: ACL, rate limiting, protocol evaluation, type constraints — no agent modification needed | [GaaS paper (arXiv, 2025)](https://arxiv.org/html/2508.18765v2) proposes this as a new architecture |
| Pathology detection | Watchdog conditions: loops, stalls, echo chambers, fan-out, context pressure, circular delegation, delivery lag | Research identifies cascade failures as critical ([Galileo AI, 2025](https://galileo.ai/blog/google-agent2agent-a2a-protocol-guide): single compromised agent poisons 87% of downstream decisions in 4h) |
| Evidence-based trust | Peer attestation + attestor credibility scoring (agreement-rate, collusion detection) | [AgentReputation (arXiv, 2026)](https://arxiv.org/abs/2605.00073) proposes similar three-layer framework |
| Agent interoperability | A2A inbound + outbound bridges, channel-mediated delegation | [A2A v1.0 (Linux Foundation, 2026)](https://www.linuxfoundation.org/press/a2a-protocol-surpasses-150-organizations-lands-in-major-cloud-platforms-and-sees-enterprise-production-use-in-first-year): 150+ organisations, production deployments |

### What the field has that qhorus doesn't yet

| Capability | Research state | Gap in qhorus |
|---|---|---|
| Formal verification of agent protocols | [ICLR 2026](https://arxiv.org/pdf/2510.14133): 30 temporal logic properties for A2A/MCP | Commitment lifecycle is a state machine but not formally verified |
| Cryptographic agent identity | [DID/VC research (arXiv, 2025)](https://arxiv.org/html/2511.02841v1), [A2A v1.0 signed cards](https://www.programming-helper.com/tech/agent-to-agent-protocol-2026-google-a2a-standard) | Agent cards are unsigned; trust is configuration-based, not cryptographic |
| Reputation-aware routing | [RepuNet (2025)](https://rnwy.com/blog/ai-reputation-systems): cooperation climbs from 20% to 85% with reputation | Trust scores exist but don't influence routing decisions |
| Cascade containment (quarantine) | [CSA governance gap report (2026)](https://labs.cloudsecurityalliance.org/research/csa-research-note-ai-agent-governance-framework-gap-20260403/) | Watchdogs detect but don't quarantine |
| Compliance evidence export | [EU AI Act enforcement August 2, 2026](https://zylos.ai/research/2026-05-01-ai-agent-governance-compliance-2026/); [71% of enterprises lack governance frameworks](https://thinking.inc/en/blue-ocean/agentic/enterprise-agent-governance/) | Ledger has the data; no regulatory packaging |
| A2A push notifications | [A2A roadmap](https://www.programming-helper.com/tech/agent-to-agent-protocol-2026-google-a2a-standard) | webhook-observer exists; no A2A-native push |

---

## Roadmap — Three Tiers

### Tier 1: Strengthen what's already there (near-term)

#### 1.1 Formal verification of the commitment lifecycle

The 10-type speech act taxonomy maps directly to deontic logic:

| Speech act category | Message types | Deontic concept |
|---|---|---|
| Directives | COMMAND, QUERY | Obligations — create duties on the receiver |
| Commissives | PROPOSE | Conditional obligations — sender commits if accepted |
| Assertives | RESPONSE, STATUS, DONE | Fulfillment evidence — discharge obligations |
| Declarations | DECLINE, FAILURE, HANDOFF | State transitions — terminate or transfer obligations |
| Expressives | EVENT | Informational — no deontic weight |

Encode the commitment state machine as temporal logic properties:

- **Liveness:** Every COMMAND eventually reaches a terminal state (FULFILLED, DECLINED, FAILED, EXPIRED). No commitment hangs forever.
- **Safety:** No commitment is fulfilled twice. No commitment transitions backward (terminal states are absorbing).
- **Fairness:** Obligations distribute equitably across agents — no single agent accumulates unbounded open commitments.
- **Deadlock freedom:** No circular dependency chain among open commitments prevents progress.

The [deontic temporal logic paper (arXiv, 2025)](https://arxiv.org/abs/2501.05765) demonstrates how to encode these as verifiable properties. The [ICLR 2026 workshop paper](https://arxiv.org/pdf/2510.14133) defines 30 such properties for A2A/MCP protocols, including safety, liveness, completeness, and fairness.

Qhorus already has the state machine (`CommitmentService`) and the ledger to verify against. The missing piece is the property specification layer — a set of temporal logic invariants that the ledger can be checked against, either at runtime (monitoring) or offline (audit).

**Connects to:** `CommitmentService` state machine, `WatchdogEvaluationService` (runtime monitoring), `MessageLedgerEntryRepository` (offline verification).

#### 1.2 Cross-channel causal reasoning

The attribution problem — "who delegated to whom in the chain that caused this failure?" — is identified by [every governance framework](https://labs.cloudsecurityalliance.org/research/csa-research-note-ai-agent-governance-framework-gap-20260403/) as the hardest question in multi-agent systems.

Qhorus's ledger has `causedByEntryId` for within-channel causality. But multi-agent systems spawn work across channels: Agent A on channel-1 sends a COMMAND that triggers Agent B on channel-2 to HANDOFF to Agent C on channel-3. When something fails on channel-3, the causal chain spans three channels.

A cross-channel causal graph built from ledger entries would give qhorus something no other framework offers: full reconstruction of multi-hop delegation chains, post-hoc, from tamper-evident evidence. The data is already in the ledger — `correlationId` and `causedByEntryId` carry the links. The missing piece is the query layer that walks these links across channel boundaries.

**Connects to:** `MessageLedgerEntryRepository.findCausalChain()` (within-channel, exists), `get_causal_chain` MCP tool (exists). Needs cross-channel extension.

#### 1.3 Active governance policies

Protocol enforcement is currently advisory — violations produce `DispatchResult.advisories()` but don't block the message. Making enforcement configurable per-channel:

| Mode | Behaviour |
|---|---|
| Advisory (current) | Log warning, include in advisories, deliver message |
| Blocking | Reject the message. Return error to sender |
| Quarantine | Deliver the message but flag the channel for operator review. Pause if threshold exceeded |

This gives deployers the [Governance-as-a-Service capability](https://arxiv.org/html/2508.18765v2) the research describes — runtime policy enforcement without modifying agent logic. The dispatch pipeline already evaluates protocols at message time; the change is the response to a violation, not the detection.

**Connects to:** `ProtocolRegistry`, `MessageService.dispatch()` enforcement gate, `ChannelProtocol` SPI.

---

### Tier 2: Close the trust loop (medium-term)

#### 2.1 Reputation-aware routing

The trust score system computes per-actor scores from attestations. The [AgentReputation paper (arXiv, 2026)](https://arxiv.org/abs/2605.00073) argues for evidence-based, contextual, decision-facing reputation — which is exactly what qhorus's attestation + credibility system already provides.

The missing step: using trust scores as routing signals. When a COMMAND targets a capability rather than a specific agent (`target: "role:analyst"` rather than `target: "agent-007"`), the capability-based addressing mode in `InstanceService.findByCapability()` returns all matching agents. Selecting the highest-trust agent with that capability would make trust actionable, not just observable.

[RepuNet research (2025)](https://rnwy.com/blog/ai-reputation-systems) demonstrated cooperation rates climb from 20% to 85% with reputation systems in multi-agent public goods games. An agent with declining trust score gets fewer delegations — creating an incentive gradient toward reliable behaviour.

**Connects to:** `TrustScoreCalculator`, `InstanceService.findByCapability()`, capability-based addressing in `MessageService.dispatch()`.

#### 2.2 Signed Agent Cards and verifiable credentials

[A2A v1.0](https://www.linuxfoundation.org/press/a2a-protocol-surpasses-150-organizations-lands-in-major-cloud-platforms-and-sees-enterprise-production-use-in-first-year) introduced cryptographic identity verification via signed agent cards. The [DID/VC research (arXiv, 2025)](https://arxiv.org/html/2511.02841v1) proposes equipping each agent with a decentralized identifier and verifiable credentials for trust anchoring.

Qhorus's `AgentCardResource` serves agent cards at `/.well-known/agent.json`. `ExternalAgentBinding` + `CredentialResolver` handle outbound authentication. Adding cryptographic signatures to agent cards would make identity verifiable, not just declared. The [ERC-8004 standard (2025)](https://rnwy.com/blog/ai-reputation-systems) extends A2A with identity registries and standardized reputation scores — qhorus's trust scoring is richer than what ERC-8004 offers, but lacks the cryptographic anchoring.

**Connects to:** `AgentCardResource`, `ExternalAgentBinding`, `CredentialResolver` SPI.

#### 2.3 Cascade failure containment

[Research shows](https://galileo.ai/blog/google-agent2agent-a2a-protocol-guide) a single compromised agent can poison 87% of downstream decisions within 4 hours — faster than human incident response can contain.

Qhorus's watchdog conditions detect pathologies but don't yet quarantine. Combining watchdog alerts with automatic containment:

1. Watchdog fires (e.g., LOOP_DETECTED, ECHO_CHAMBER)
2. Channel paused automatically (existing `ChannelService.pause()`)
3. Backend deregistered for the offending agent
4. `CommitmentExpiredEvent` fires for stalled obligations
5. Operator notified via notification bridge

This turns detection into containment. The building blocks exist — the missing piece is the policy layer that connects watchdog alerts to containment actions.

**Connects to:** `WatchdogEvaluationService`, `ChannelService.pause()/resume()`, `ChannelGateway.deregisterBackend()`, notification bridge.

---

### Tier 3: Ecosystem positioning (longer-term)

#### 3.1 Compliance evidence export

The [EU AI Act enforcement deadline is August 2, 2026](https://zylos.ai/research/2026-05-01-ai-agent-governance-compliance-2026/). [71% of enterprises deploying agents lack a governance framework](https://thinking.inc/en/blue-ocean/agentic/enterprise-agent-governance/). [Gartner reports](https://labs.cloudsecurityalliance.org/research/csa-research-note-ai-agent-governance-framework-gap-20260403/) organisations applying traditional AI governance to agentic systems miss 60-70% of agent-specific risk vectors.

Qhorus's tamper-evident ledger is exactly the audit trail the regulation demands. The PROV-DM export example already exists. Packaging ledger data into compliance-ready formats:

- Attribution chain reports (who delegated what to whom)
- Obligation fulfillment rates per agent and channel
- Trust score history and attestation evidence
- Protocol violation logs with enforcement actions
- Automated Art. 12 transparency reports

**Connects to:** `MessageLedgerEntryRepository`, PROV-DM export example, trust score system, protocol enforcement logs.

#### 3.2 A2A-native push notifications

The [A2A roadmap](https://www.programming-helper.com/tech/agent-to-agent-protocol-2026-google-a2a-standard) includes push notifications for bidirectional real-time coordination. Qhorus's `webhook-observer` module handles HTTP POST callbacks. Adding A2A-native push — notifying external agents of channel events using A2A's own notification protocol — would make qhorus a full bidirectional participant in the [A2A ecosystem of 150+ organisations](https://www.prnewswire.com/news-releases/a2a-protocol-surpasses-150-organizations-lands-in-major-cloud-platforms-and-sees-enterprise-production-use-in-first-year-302737641.html), not just an adapter.

**Connects to:** `webhook-observer`, `A2AOutboundBackend`, `A2AChannelBackend`, `CloudEventMapper`.

#### 3.3 Context-aware work redistribution

The CONTEXT_PRESSURE watchdog detects when agents are running hot. The channel summary infrastructure can compress context. The missing capability: when an agent's context window fills up, automatically redistribute its open obligations to other capable agents via HANDOFF — using trust scores to select the delegate.

This would make qhorus the first framework to handle context window management as a governance concern rather than leaving it to each agent individually. The delegation chain is recorded in the ledger; the trust system validates the delegate; the commitment lifecycle tracks the handoff.

**Connects to:** `CONTEXT_PRESSURE` watchdog, `ChannelSummaryService`, `CommitmentService.delegate()`, trust-score routing (Tier 2.1).

---

## The compounding argument

The three tiers compound. Formal verification (1.1) proves the commitment lifecycle is sound. Active governance (1.3) makes enforcement configurable. Reputation-aware routing (2.1) makes trust actionable. Cascade containment (2.3) makes detection automatic. Compliance export (3.1) makes the whole system auditable for regulators. Each tier makes the next one more valuable.

The strategic claim: frameworks that start from governance and add capability will outlast those that start from capability and bolt on governance. The [research is clear](https://www.sciencedirect.com/science/article/pii/S2666651026000069) that post-hoc governance fails in agentic systems because the enforcement surface is too large and too dynamic. Qhorus's architecture — where every interaction is an accountable act by design — is the right foundation.

---

## Sources

### Governance and regulatory
- [The AI Agent Governance Gap (Cloud Security Alliance, 2026)](https://labs.cloudsecurityalliance.org/research/csa-research-note-ai-agent-governance-framework-gap-20260403/)
- [Model AI Governance Framework for Agentic AI (Singapore IMDA)](https://www.imda.gov.sg/-/media/imda/files/about/emerging-tech-and-research/artificial-intelligence/mgf-for-agentic-ai.pdf)
- [AI Agent Governance & Compliance 2026 (Zylos Research)](https://zylos.ai/research/2026-05-01-ai-agent-governance-compliance-2026/)
- [Enterprise Agent Governance Framework (Thinking Inc, 2026)](https://thinking.inc/en/blue-ocean/agentic/enterprise-agent-governance/)
- [TRiSM for Agentic AI (ScienceDirect, 2026)](https://www.sciencedirect.com/science/article/pii/S2666651026000069)

### Formal verification and deontic logic
- [Deontic Temporal Logic for Formal Verification of AI Ethics (arXiv, 2025)](https://arxiv.org/abs/2501.05765)
- [Formal Verification of Agent Protocols (ICLR 2026 Workshop)](https://arxiv.org/pdf/2510.14133)
- [XMPro MAGS Deontic Principles](https://github.com/XMPro/Multi-Agent/blob/main/docs/concepts/deontic-principles.md)
- [Norm Emergence in Multi-Agent Systems (arXiv, 2024)](https://arxiv.org/html/2412.10609v1)
- [Formalizing Norms in MAS: T-Norm Model (Springer, 2024)](https://link.springer.com/article/10.1007/s42979-024-03052-4)

### Coordination and transparency
- [Coordination Transparency: Governing Distributed Agency (Springer AI & Society, 2026)](https://link.springer.com/article/10.1007/s00146-026-02853-w)
- [Governance-as-a-Service: Multi-Agent Compliance Framework (arXiv, 2025)](https://arxiv.org/html/2508.18765v2)
- [Agentifying Agentic AI (AAAI 2026 Bridge Program)](https://arxiv.org/html/2511.17332v2)

### Trust and reputation
- [AgentReputation: Decentralized Agentic AI Reputation Framework (arXiv, 2026)](https://arxiv.org/abs/2605.00073)
- [AI Reputation Systems: Trust in a Multipolar AI Ecosystem (RNWY)](https://rnwy.com/blog/ai-reputation-systems)
- [AI Agents with Decentralized Identifiers and Verifiable Credentials (arXiv, 2025)](https://arxiv.org/html/2511.02841v1)
- [Building Trust Systems for AI Agent Teams (Dev|Journal, 2026)](https://earezki.com/ai-news/2026-02-25-building-trust-systems-for-ai-agent-teams-beyond-individual-credit-scores/)

### A2A protocol ecosystem
- [A2A Protocol One-Year Milestones (Linux Foundation, 2026)](https://www.linuxfoundation.org/press/a2a-protocol-surpasses-150-organizations-lands-in-major-cloud-platforms-and-sees-enterprise-production-use-in-first-year)
- [A2A Protocol 2026 Standard (Programming Helper)](https://www.programming-helper.com/tech/agent-to-agent-protocol-2026-google-a2a-standard)
- [Google A2A Protocol Guide (Galileo AI)](https://galileo.ai/blog/google-agent2agent-a2a-protocol-guide)
- [MCP vs A2A: Complete Guide (DEV Community, 2026)](https://dev.to/pockit_tools/mcp-vs-a2a-the-complete-guide-to-ai-agent-protocols-in-2026-30li)
- [AI Agent Protocol Ecosystem Map 2026 (Digital Applied)](https://www.digitalapplied.com/blog/ai-agent-protocol-ecosystem-map-2026-mcp-a2a-acp-ucp)
- [A2A Protocol Adoption Reality (Glukhov, 2026)](https://www.glukhov.org/ai-systems/comparisons/a2a-protocol-2026-adoption/)
