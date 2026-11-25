# Normative Layer — Summary and Critique

*A reading guide for the normative body of work. Summarises how the
documents layer and support each other, identifies the gaps, and frames
what needs to be strengthened. This document is a living critique — updated
as the body of work grows.*

---

## The Core Documents

| Document | Role |
|---|---|
| [normative-layer.md](normative-layer.md) | Composition thesis and business case — the *why* |
| [agent-mesh-framework.md](agent-mesh-framework.md) | Machine-agent developer guide — the *how for code* |
| [work-and-workitems.md](work-and-workitems.md) | Human-agent normative extension — the *how for people* |

Each addresses a distinct layer without overlapping the others. They reference
each other precisely and build without duplication.

---

## How They Layer

### normative-layer.md — The Composition Thesis

Establishes the case for three orthogonal capabilities that compose into
governed accountability:

- **Communication governance** — template registry + NLP extraction. Governance
  acts in prose are mechanically parsed into structured records with typed
  fields. Schema publication closes the loop — tell agents what to say,
  extract mechanically. Grounded in structural linguistics and lemmatisation.

- **Normative accountability** — the 10-type speech act taxonomy, 7-state
  commitment lifecycle, tamper-evident ledger, Bayesian trust scoring, peer
  attestation. Grounded in speech act theory, deontic logic, defeasible
  reasoning, and social commitment semantics — a 30-year academic lineage.

- **Protocol enforcement** — channel protocols, playbook bridge, enforcement
  modes (advisory/blocking/quarantine), handshake gating. Governed outcomes
  are gated on participant agreement over template-extracted fields — not
  message count.

The business framing is direct: the eight questions regulators, auditors, and
boards actually ask are each answered by a query or a composition of
capabilities. Not by log archaeology. By infrastructure that was built as
the system ran.

The three worked examples demonstrate increasing composition:
- **Insurance claim** — normative accountability with light template annotations
- **Production incident** — every speech act type under pressure, template
  extraction adding structured evidence
- **Governed architecture review** — full composition loop: perception +
  enforcement + accountability producing a governed voting outcome

### agent-mesh-framework.md — The Machine Layer

Translates the composition into developer-facing infrastructure. The 10-type
message taxonomy becomes an enum. The CommitmentStore lifecycle becomes a
state machine with a clear API. The ledger becomes a query surface with
query tools. The template registry becomes a YAML corpus and registration API.

The NormativeChannelLayout — three channels per case: `work` (all types),
`observe` (EVENT only), `oversight` (QUERY+COMMAND only) — is the principal
structural contribution.

The anti-patterns section is as important as the API: EVENT on the work
channel, silent failure, obligation flooding, large payloads in message
bodies — each one describes what the CommitmentStore looks like when the
pattern is violated.

### work-and-workitems.md — The Human Layer

The key argument: machine-agent and human-agent obligations differ in one
fundamental dimension — **obligation continuity**. A machine agent can wait
hours for a long-running process, block on a signal, or hold an obligation
open across many STATUS updates — duration is not the distinction. What a
machine agent does not do is hold an obligation *discontinuously*: set it
aside, attend to other things, and return.

A human agent holds obligations discontinuously by nature — stepping away,
switching context, partially delegating while retaining accountability,
returning after interruption. Every state WorkItem introduces beyond the
machine-layer minimum — ACKNOWLEDGED, IN_PROGRESS, SUSPENDED, DELEGATED —
is a normative artifact of that discontinuous holding, not of duration.

The seam between layers is the oversight channel. Layer 1 has a synthetic
human posting COMMAND directly. Layer 2 replaces that synthetic post with
a real WorkItem lifecycle — claim, review, possibly delegate, resolve — and
the resulting COMMAND feeds back into the mesh. The channel is unchanged.
The message types are unchanged. The seam is invisible to the machine layer.

---

## What the Documents Establish Together

**The composition model is the primary achievement.** Three orthogonal
capabilities — each with independent formal grounding, each independently
useful, each opt-in — that compose into a governance loop no single
capability can produce alone. The theory → implementation → extension
vertical is preserved, with each layer adding genuine capability without
modifying the layer below.

**Formal completeness is a real property, not a claim.** The 10-type
taxonomy is derived from the formal literature. FAILURE and DECLINE are
not interchangeable status values; they are categorically distinct speech
acts with different CommitmentStore transitions and different operational
implications. Template schemas extend this to governance vocabularies —
formal sentence patterns that produce convergence across independently
trained LLMs.

**The composition is additive and opt-in.** A channel can use normative
accountability alone (message types, commitments, ledger). Adding
communication governance adds perception — prose parsed into structured
acts. Adding protocol enforcement adds rules — governance gated on
participant agreement. Each combination produces value; removing a
capability reduces what is governed, not what is possible.

**The extension model is clean.** SUSPENDED and sub-delegation are
correctly identified as human-layer concepts with no machine-layer
equivalent and no semantic overlap with any existing type.

---

## Gaps and Critique

### 0. Channel Backend Abstraction *(implemented — casehubio/qhorus#131)*

The `ChannelGateway` is implemented. Qhorus is now backend-agnostic:
messages can be delivered to external transports without changes to the
normative layer. All 10 message types flow through `MessageService` and
`LedgerWriteService`. The normative pipeline is preserved regardless
of backend.

### 1. Communication Governance Perception Layer *(implemented — casehubio/platform#943)*

The template registry provides mechanical extraction of governance acts
from agent prose. The unified registry (#959) consolidates the extraction
pipeline. NLP structural matching handles grammatical variation (tense,
number, determiners). Schema publication tells agents what sentence shapes
the protocol expects. Bidirectional generation produces conformant prose
from structured fields.

**Current limitation: paraphrase tolerance.** Structural matching handles
inflection ("approved" → "approve") but not arbitrary paraphrase ("I'm
in favour of this" vs. "I approve this"). Adaptive phrase variation —
learning known variations over time via LLM fallthrough — addresses this
architecturally. The matching tier is pluggable: NLP structural matching
today, adaptive variation as it matures.

**Related platform issues:**
- casehubio/platform#969 — YAML corpus format
- casehubio/platform#970 — `match` playbook step
- casehubio/platform#972 — atomic namespace update with version safety
- casehubio/platform#973 — NearMiss guided rejection
- casehubio/platform#974 — corpus quality testing tooling

### 2. Cross-Channel Causal Correlation *(implemented)*

`causedByEntryId` resolves across all channels. `get_causal_chain` traverses
channel boundaries. The remaining open question is ProvenanceLink (PROV-O
graph across agents, cases, and WorkItems).

### 3. WorkItem Integration into Trust Scoring

The machine layer has a closed feedback loop: `LedgerWriteService` writes
attestations on DONE, FAILURE, and DECLINE. The human layer does not yet
close this loop. When a WorkItem reaches COMPLETED, does that produce a
LedgerAttestation? Who is the attestor? How do sub-delegation chains
affect EigenTrust propagation?

### 4. The Extension Contract

Template namespaces provide the extension mechanism for governance
vocabularies — a protocol author defines a namespace, registers templates,
and the registry handles composition. The question that remains: how do
domain-specific governance vocabularies compose with the base normative
vocabulary? Can two namespaces define conflicting patterns? What happens
when a namespace is deprecated?

The broader extension contract — namespacing for new speech act types,
declaration mechanisms for the CommitmentStore and ledger, promotion
criteria — is described as needed but not yet specified.

### 5. The Empirical Hypothesis Remains Unverified

normative-layer.md makes a specific claim: that formal semantic grounding
produces convergence across independently trained LLM agents. This is
framed honestly as a hypothesis (engine#189). But it is load-bearing.
The empirical test needs to run.

### 6. Corpus Quality Tooling *(casehubio/platform#974)*

There is no tooling for validating whether a corpus handles the variation
a specific LLM will produce. A team authors templates, deploys them, and
discovers in production that different LLMs phrase governance acts
differently. Match-rate testing, regression fixtures, and cross-namespace
collision detection are needed before corpus authoring is safe for
production governance.

### 7. Hot-Update Safety *(casehubio/platform#972)*

Template namespace replacement is non-atomic (`deregister` then `register`).
Channels with active commitments can experience a matching gap during
updates. Version metadata and atomic swap are needed.

---

## What Needs Strengthening

In rough priority order:

1. **Corpus quality tooling** — Validate templates against real LLM
   variation before deployment. The composition model's value depends
   on extraction reliability.

2. **WorkItem → LedgerAttestation path** — Close the trust feedback loop
   for human obligations.

3. **Extension contract specification** — Namespace composition rules,
   deprecation, promotion criteria. Design this before external extensions
   exist.

4. **Layer 2 canonical test** — An executable end-to-end test for the
   oversight channel + WorkItem lifecycle path.

5. **engine#189 empirical test** — Run the LLM convergence experiment.

---

## Related Documents

| Document | What it covers |
|---|---|
| [normative-framework.md](normative-framework.md) | Navigation entry point for the full body of works |
| [normative-channel-layout.md](normative-channel-layout.md) | Three-channel pattern and `MessageTypePolicy` SPI reference |
| [normative-objections.md](normative-objections.md) | Counter-arguments to the normative layer approach |
| [multi-agent-framework-comparison.md](multi-agent-framework-comparison.md) | Feature comparison against AutoGen, LangGraph, CrewAI, and others |
| [agent-protocol-comparison.md](agent-protocol-comparison.md) | Positioning against A2A and ACP |

*Qhorus — the agent communication mesh for the Quarkus Native AI Agent Ecosystem.*
*Platform context: [CaseHub Platform](https://github.com/casehubio/parent/blob/main/docs/PLATFORM.md)*
