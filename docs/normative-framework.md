# The Qhorus Normative Framework — Body of Works

The normative layer is the accountability infrastructure that multi-agent AI has been missing.
Three orthogonal capabilities — communication governance, normative accountability, and protocol
enforcement — compose into a closed governance loop from natural language to auditable, enforced
action. These documents explain what it is, why it matters, how it compares to alternatives, and
how it fits into the broader agent communication landscape.

---

## Reading Order

**Start here to understand the composition:**
→ [The Qhorus Normative Layer](normative-layer.md) — the thesis, the three capabilities, the
worked examples, and why they compose

**Then read this to build with it:**
→ [Agent Mesh Framework — Developer Guide](agent-mesh-framework.md) — message vocabulary,
channel topology, agent lifecycle, template-based governance, and worked examples

**Then read this to see how it compares:**
→ [Multi-Agent Framework Comparison](multi-agent-framework-comparison.md) — feature-level
comparison against AutoGen, LangGraph, CrewAI, Gastown, and ten other frameworks

**Then read this to understand where it fits in the protocol landscape:**
→ [Agent Protocol Comparison](agent-protocol-comparison.md) — how Qhorus, A2A, and ACP are
complementary rather than competing

---

## What Each Document Covers

### [normative-layer.md](normative-layer.md)
The foundational document. Covers:
- What the normative layer is and why accountability cannot be retrofitted
- The three capabilities that compose: communication governance (template registry + NLP),
  normative accountability (commitments, ledger, trust), and protocol enforcement (playbooks,
  handshake gating)
- The formal foundations — speech act theory, deontic logic, structural linguistics, and
  social commitment semantics — and why each grounds a specific capability
- Why formal semantics produce convergence where hand-rolled protocols diverge
- What the normative layer enables in practice (eight questions it answers)
- Three worked examples: an insurance claim (regulated industry), a production incident
  (software engineering), and a governed architecture review (full composition loop)
- The empirical hypothesis and the experiment designed to test it

### [agent-mesh-framework.md](agent-mesh-framework.md)
The developer guide. Covers:
- The 10 message types in three groups — information exchange, obligation lifecycle, telemetry
- Channel semantics and the NormativeChannelLayout (3-channel topology)
- Agent lifecycle — register, announce, work loop, completion
- The CommitmentStore — the 7-state obligation lifecycle
- The normative ledger — query tools and when to use each
- Template-based communication governance — YAML corpus, NLP extraction, schema publication,
  enforcement wiring
- Human-in-the-loop — oversight channel, approval gate, watchdogs
- Anti-patterns and quick-start template

### [multi-agent-framework-comparison.md](multi-agent-framework-comparison.md)
The technical comparison. Covers:
- Normative layer capabilities: which frameworks have commitment lifecycle tracking,
  accountability attribution, trust models, stalled obligation detection
- Communication and coordination features across all frameworks
- Coordination and communication features across all frameworks
- What Qhorus uniquely provides and where other frameworks genuinely excel

### [agent-protocol-comparison.md](agent-protocol-comparison.md)
The protocol landscape. Covers:
- How A2A (Google), ACP (IBM/BeeAI), and Qhorus answer different questions
- Why they are complementary rather than competing
- How a production system uses all three at different layers

### [normative-summary.md](normative-summary.md)
The critique and gap analysis. Covers:
- How the core documents layer and support each other
- Known gaps: paraphrase tolerance, corpus quality tooling, hot-update safety
- What needs strengthening: empirical hypothesis, WorkItem trust integration, extension contract

---

## The Central Claim

Governing agent communication requires solving three problems independently: perception
(what did the agent say?), accountability (who owes what to whom?), and enforcement (is it
permitted?). Each has a formal foundation. Each is independently useful. Together they produce
something none can produce alone — a closed loop from natural language to governed accountability.

**Without formal semantic grounding, independently implemented agent systems will each invent
their own vocabulary for the same coordination concepts** — a Tower of Babel where independently
built agents cannot be reliably composed. Formal semantics are the only mechanism that has
historically produced interoperability between independently built agent systems: FIPA-ACL
succeeded where KQML failed for exactly this reason. Formal sentence schemas extend this to
governance vocabularies.

**The empirical test:** [engine#189](https://github.com/casehubio/engine/issues/189) is a
structured experiment comparing LangChain4j (no normative layer) against CaseHub + Qhorus
(normative layer enforced at protocol level) on a production incident response scenario.

---

*Part of the [CaseHub platform](https://github.com/casehubio/parent/blob/main/docs/PLATFORM.md).
Normative layer implemented in [casehubio/qhorus](https://github.com/casehubio/qhorus).
Theoretical foundation documented in ADR-0005.*
