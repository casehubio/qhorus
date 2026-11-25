# The Qhorus Normative Layer

## What It Is and Why It Matters for Enterprise Agentic-AI

**Capability without accountability is not progress — it is liability in waiting.**

As AI agents take on consequential roles in the world — approving claims, processing payments,
filing regulatory reports, routing financial decisions — a second question matters as much as
whether they can perform the task: *can we prove what each agent committed to, to whom, under
what authorisation, and what happened when they failed to deliver?* Human institutions spent
centuries developing the infrastructure to answer that question: contracts, audits, professional
accountability frameworks, clearing houses, regulatory reporting. Multi-agent AI has built
remarkable capability while leaving that infrastructure entirely absent.

This is not a logging problem. Logs record events; they do not capture commitments. This is not
a workflow orchestration problem; orchestration tracks procedural steps, not deontic obligations
between autonomous agents. This is not a model quality problem; even perfectly-reasoning agents
operating without formal accountability infrastructure cannot produce the evidence that regulated
environments require.

And it is not a parsing problem. An agent that writes "I approved this architecture because it
handles failover" has performed a governance act. Without mechanical extraction, that act exists
only as a string in a log — indistinguishable from a comment, a suggestion, or a greeting.
Accountability requires that consequential acts be identified, structured, enforced against
protocol rules, and recorded in a form that is independently auditable. At some point, agent
conversation must result in something actionable — a function call, an event, a normalised
structure — or the conversation produces no accountability at all, which in regulated
environments is dangerous.

The normative layer is the accountability infrastructure that multi-agent AI has been missing.
It comprises three orthogonal capabilities — each independently useful, each addressing a
distinct gap — that compose into a closed governance loop from natural language to auditable,
enforced action.

**For business owners and compliance officers, the significance is not the technology — it is
the methodology.** The normative layer provides a formally-grounded governance framework for AI
that maps directly to the questions regulators, auditors, and boards already ask: what was
authorised, by whom, what obligations were created, what evidence exists, and who is accountable
when something goes wrong. This is not another layer added to the middleware stack. It is the
foundation that determines whether AI agents can operate legitimately in regulated environments —
and the methodology that gives organisations a defensible, auditable answer when accountability
is demanded. That answer cannot be retrofitted. It has to be built in from the start.

---

## Three Capabilities That Compose

Governing agent communication requires solving three problems independently. Each is necessary.
None is sufficient alone. The problems are orthogonal — they can be solved in any order, with
any subset, and each solution adds value without requiring the others. But when all three are
present, they produce something none can produce individually: a closed loop from natural
language to governed accountability.

```
+================================================================================+
|  THE QHORUS NORMATIVE COMPOSITION                                              |
|  Three capabilities — each independent, together a closed governance loop     |
+================================================================================+

+------------------------------+     +------------------------------+
|  COMMUNICATION GOVERNANCE    |     |  PROTOCOL ENFORCEMENT        |
|  "What did the agent say?"   |     |  "Is it permitted?"          |
|                              |     |                              |
|  Template registry + NLP     |     |  Channel protocols           |
|  YAML corpus definitions     |     |  Playbook bridge             |
|  Structural extraction       |     |  Enforcement modes           |
|  Schema publication          |     |  Handshake gating            |
|  Bidirectional generation    |     |  Dispatch advisories         |
+--------------+---------------+     +---------------+--------------+
               |   extracts governance acts           |   evaluates rules
               |   from prose                         |   gates outcomes
               v                                      v
+================================================================================+
|  NORMATIVE ACCOUNTABILITY                                                      |
|  "Who owes what to whom — and what happened?"                                 |
|                                                                                |
|  10-type speech act taxonomy -- every message is a performative                |
|  7-state commitment lifecycle -- obligations tracked to resolution             |
|  Tamper-evident ledger -- SHA-256 hash chain, causally linked                 |
|  Bayesian trust scoring -- reputation derived from behaviour                   |
|  Peer attestation -- independent verification of outcomes                      |
+================================================================================+
```

### Communication Governance

**Governance without perception is manual.** An agent posts "I approved this architecture
because it handles failover." Without mechanical extraction, a human must read that message,
determine it is an approval, identify the subject and rationale, and feed those into whatever
governance process applies. At five agents this is tedious. At fifty agents running overnight
across four channels, it is impossible.

Communication governance solves perception. A template registry defines the vocabulary of
governance acts — what sentence shapes the protocol expects, what variables they contain, and
what constraints those variables carry. NLP extraction parses agent prose against those templates,
tolerating the grammatical variation that LLMs naturally produce — verb tense, number, determiners
— while enforcing structural and semantic constraints. The result is a structured governance
record: not "the agent said something that looked like an approval" but
`{template: "vote", decision: "approve", subject: "architecture", rationale: "handles failover"}`
— with typed fields, constrained values, and deterministic extraction.

The extraction is mechanical and auditable. No LLM participates in the parsing. The template
defines the structure; the NLP pipeline handles variation; the registry produces typed fields
or reports no match. This determinism is the point — governance acts must be identified by
infrastructure, not inferred by another model.

Templates are **bidirectional**: the same definition that extracts fields from prose also
generates conformant prose from fields. The infrastructure can tell an agent exactly what
sentence shape a protocol expects (schema publication), and it can produce a governance summary
from extracted fields ("The committee approved the architecture 2-1. Security-lead voted reject,
citing lack of tests."). Extraction and generation are inverses — a round trip preserves every
field.

The governance vocabulary is defined declaratively in YAML corpus files. A protocol author writes
templates with variable constraints; they do not write parsers, regex patterns, or NLP pipelines.
The registry compiles the templates, indexes them by lemmatised keywords for fast candidate
selection, and matches at runtime.

This is not NLP for convenience. It is the perception layer that makes agent governance
mechanical rather than manual. Without it, every governance act is a prose string that someone —
or some other model — must interpret. With it, every governance act is a typed, constrained,
deterministic extraction that feeds directly into the enforcement pipeline.

### Normative Accountability

**Accountability without tracking is aspiration.** An agent performs a governance act — an
approval, a delegation, a refusal. Without formal accountability infrastructure, that act exists
in application state: a status field, a log entry, a message in a queue. When something goes
wrong — the approval was unauthorised, the delegation chain went cold, the refusal was
unwarranted — the investigation requires reconstructing what happened from disparate sources
that were never designed to carry evidential weight.

Normative accountability solves tracking. Grounded in thirty years of research at the
intersection of speech act theory, deontic logic, defeasible reasoning, and social commitment
semantics, it gives every agent interaction the formal status of an accountable act: recorded,
tracked, causally linked, tamper-evidently proven, and queryable by the agents themselves at
runtime.

The 10-type message taxonomy classifies every communicative act:

| Type | What it does | Obligation effect |
|------|-------------|-------------------|
| QUERY | Ask for information | Creates weak expectation |
| COMMAND | Ask for action | Creates obligation |
| RESPONSE | Answer a QUERY | Discharges expectation |
| STATUS | Report progress | Extends obligation window |
| DECLINE | Refuse a QUERY/COMMAND | Terminates with stated reason |
| HANDOFF | Transfer obligation | Delegates formally |
| DONE | Successful completion | Fulfills obligation |
| FAILURE | Failed completion | Terminates with explanation |
| PROPOSE | Propose an agreement | Creates negotiable commitment |
| EVENT | Telemetry/observability | No obligation created |

The CommitmentStore tracks the lifecycle of every obligation through seven states:
`OPEN → ACKNOWLEDGED → FULFILLED / DECLINED / FAILED / DELEGATED / EXPIRED`. Infrastructure
tracks who owes what to whom — not the LLM.

The normative ledger records every speech act as a `MessageLedgerEntry` in a SHA-256 hash chain.
`causedByEntryId` links each resolution back to the act that created it. Tamper-evident.
Queryable. The proof that an obligation existed and how it was resolved — permanently.

Bayesian Beta trust scoring computes a per-actor score from the attestation record. Peer
attestation provides independent verification. EigenTrust propagates trust transitively across
the mesh. Trust is computed from behaviour, not asserted by configuration. The full trust model
— including how communication governance feeds structural conformance signals into scoring — is
described below under "Trust derived from behaviour."

### Protocol Enforcement

**Governance without enforcement is advisory.** An agent produces a governance act — an approval,
a vote, an escalation. Without enforcement, the act is recorded and nothing else happens. The
protocol rules — who is allowed to approve, whether all participants have voted, whether a
threshold has been reached — exist only in documentation or in the heads of the team that
configured the system.

Protocol enforcement solves rules. Channel protocols evaluate governance rules at dispatch time
and produce advisories — structured reports of what the rules say about the message being
dispatched. Enforcement modes determine consequence: ADVISORY (record the advisory, dispatch the
message), BLOCKING (reject the message, record why), or QUARANTINE (pause the channel, expire
active commitments, record the violation).

The critical innovation is **handshake gating**. When a protocol requires all participants to
weigh in before an outcome takes effect, the enforcement pipeline does not count messages — it
evaluates template-extracted fields. A vote counts when the template extraction confirms the
content is a structurally matched vote with a valid decision value. An approval takes effect
when the protocol confirms all required participants have cast structurally matched approvals.
Not "has everyone sent a message" but "has everyone voted, and do the extracted fields constitute
agreement."

This is the natural join of perception and enforcement. Without communication governance,
protocol enforcement can only evaluate metadata — message count, sender identity, timing. With
it, enforcement evaluates content — what was decided, by whom, with what rationale.

Protocols are defined declaratively in YAML playbooks and registered per channel. Built-in
protocols handle common patterns: `REQUEST_RESPONSE` (threshold on open queries),
`TASK_COMPLETION` (threshold on open commands), `ROUND_ROBIN` (turn-taking),
`CONTRIBUTION_REQUIRED` (participation gating). Custom protocols — including template-aware
governance protocols — compose via the playbook bridge with the same enforcement pipeline.

### The Composition

Each capability works independently. The composition is additive and opt-in.

A channel with only normative accountability tracks obligations via message types. No templates,
no enforcement — agents declare their intent through typed messages, and the infrastructure
records the lifecycle.

A channel with communication governance and normative accountability adds perception: governance
acts in prose are mechanically extracted, and the structured fields are recorded alongside the
obligation lifecycle. The ledger entry carries not just "DONE" but
`{decision: "approve", subject: "architecture", rationale: "handles failover"}`.

A channel with all three adds enforcement: the extracted fields are evaluated against protocol
rules, outcomes are gated on participant agreement, and violations produce advisories or blocks.
The governance loop closes: prose in → structured extraction → rule evaluation → handshake
gating → enforced outcome → auditable ledger entry.

```
Agent prose
  → [Communication Governance]  Template extraction — structured fields (optional)
    → [Normative Accountability]  Speech act classification + commitment lifecycle
      → [Protocol Enforcement]  Rule evaluation + handshake gating (optional)
        → Enforcement gate  ADVISORY / BLOCKING / QUARANTINE (optional)
          → Normative ledger entry — tamper-evident, causally linked
```

Removing a capability reduces what is governed, not what is possible. The pipeline degrades
gracefully in both directions: a channel with no templates processes messages by type alone; a
channel with no normative layer can still extract governance acts from prose (without
accountability tracking). Each combination produces value; the full composition produces
accountability.

---

## Formal Foundations — Why Each Capability Exists

The capabilities above did not emerge from engineering convenience. Each addresses a problem that
has a formal treatment in the academic literature — and that formal treatment is precisely why
the capability works correctly, composes reliably, and extends without breaking.

### Speech act theory → Communication governance + Normative accountability

The first serious attempt to formalise inter-agent communication came from **FIPA** (Foundation
for Intelligent Physical Agents), an IEEE standards body active from the late 1990s. FIPA's
Agent Communication Language drew directly on Searle's speech act theory and defined a taxonomy
of communicative acts — `inform`, `request`, `agree`, `refuse`, `failure` — grounded in the
insight that messages between agents are not data transfers but *performatives* that create
effects by virtue of being uttered.

The 10-type message taxonomy inherits this directly. COMMAND is a directive speech act that
creates an obligation. DECLINE is a commissive cancellation. HANDOFF is a formal delegation.
These are not status values — they are categorically distinct acts with different formal
consequences.

The convergence argument extends to communication governance: **formally defined sentence schemas
produce convergence across independently trained LLMs in the same way that formally defined
message types produce convergence across agent frameworks.** Without formal schemas, every team
invents its own governance vocabulary — the same Tower of Babel that KQML produced for message
types. FIPA-ACL corrected this for communicative acts by grounding the taxonomy in Searle's
theory. The template registry corrects it for governance vocabularies by grounding sentence
patterns in structural linguistics — lemmatised, POS-tagged, deterministic.

### Deontic logic → Normative accountability

**Deontic logic** — the formal study of obligation, permission, and prohibition (Meyer, 1988;
Governatori et al.) — provides the theoretical foundation for the CommitmentStore. The seven-state
lifecycle is not an arbitrary state machine; it is a formal encoding of how obligations are
created, transferred, excused, and resolved in normative systems. Defeasible deontic logic
addresses the reality that obligations are not absolute — they can be overridden with reason.
HANDOFF, DECLINE, and FAILURE are first-class exception-handling mechanisms, not error conditions.

### Social commitment semantics → Normative accountability

**Social commitment semantics** (Singh, 1998; Winikoff, 2007) proposed that the right primitive
for multi-agent systems was not the agent's internal mental state but the observable commitment
between agents — what one agent has promised another, whether it has been fulfilled, and what
evidence exists. The normative ledger is a direct implementation of this idea. Every commitment
is observable, queryable, and independently verifiable.

### Structural linguistics → Communication governance

The template registry's extraction pipeline draws on a different formal tradition: structural
linguistics. Lemmatisation reduces inflected words to their base form — "approved" → "approve",
"these" → "this" — allowing templates to tolerate the grammatical variation LLMs produce while
maintaining deterministic extraction. POS tagging identifies the grammatical role of each token.
Structural matching aligns lemmatised input against compiled template patterns, extracting typed
fields from clause positions.

This is not approximate matching. The extraction is deterministic: given a template and an input,
the output is either a precise set of typed fields or a definitive no-match. The NLP pipeline
adds tolerance, not fuzziness.

### Two traditions, one system

On one side: FIPA, deontic logic, and social commitment semantics — formal agent theory from the
1990s. On the other: AutoGen, LangGraph, CrewAI, OpenAI Swarm — empirical engineering from the
2020s. The modern frameworks arrived at the same coordination patterns through building
production systems — handoff mechanisms, role-based routing, failure handling — without reference
to the academic tradition. The result is software that works but lacks formal grounding, which
means it is hard to reason about correctness, impossible to audit systematically, and difficult
to extend without breaking assumptions.

**These two traditions are not competing approaches — they are two perspectives on the same
underlying system**, expressed at different levels of abstraction. Qhorus unifies them. The
10-type taxonomy is grounded in speech act theory, but a developer reads it as an enum. The
CommitmentStore implements deontic lifecycle tracking, but a developer sees it as a state machine.
The template registry implements structural linguistic extraction, but a protocol author sees it
as a YAML file with variable constraints. You do not need to know the theory to use it
correctly — but the theory is what ensures that using it correctly is possible in the first place.

A reasonable objection is that formal frameworks have a poor track record of practitioner
adoption. FIPA, OWL-S, WSMO, formal contract languages for web services — academics built them;
practitioners ignored them. Why should this be different? Because every failed formal framework
tried to be the *product*. FIPA-ACL was the interface developers wrote to. The formal theory was
exposed directly to practitioners who had no interest in it. Qhorus inverts this. The formal
theory is invisible infrastructure. A developer calls `decline_commitment(id, reason)` — they do
not need to know that DECLINE is a commissive cancellation, or that it maps to defeasible
reasoning. A protocol author writes a YAML template — they do not need to know that the
extraction uses lemmatised structural matching. The theory is the guarantee that the API is
coherent and complete; it is not the API.

---

## From Status Fields to Accountability

A reasonable objection to the normative layer is that its distinctions — DECLINED vs FAILED vs
EXPIRED — are just status values. Any system can add status fields. What makes formally grounded
speech acts different?

The difference is between **tracking** and **accountability**. A status field records what state
a piece of work is in. A commitment records who is *accountable* for it — what they promised, to
whom, under what authorisation, and whether they followed through. These answer different
questions, and the difference becomes consequential when something goes wrong.

**The status field version of a production incident:**
An agent does not complete its assigned work. The system sees a timeout and re-assigns. Whether
the agent explicitly refused, started and failed, timed out, or silently handed to someone
else — all of these look identical. The re-assignment is the same regardless of cause. The
investigation, if it happens, requires reading logs that were never designed to carry evidential
weight.

**The accountability version of the same incident:**
The commitment state carries the distinction:

| What happened | Status field sees | Commitment sees | Correct response |
|---------------|------------------|-----------------|-----------------|
| Agent can't do this | Timeout | DECLINED — with stated reason | Re-route immediately; agent is fine |
| Agent tried and failed | Timeout | FAILED — with explanation | Investigate the agent before re-using |
| Agent handed to specialist | Timeout | DELEGATED — new obligor named | Track the chain; do not re-assign |
| Deadline passed, no response | Timeout | EXPIRED — precise timestamp | Escalate; something systemic is wrong |

At five agents this barely matters — you can investigate manually. At fifty agents running
overnight, these distinctions determine whether the system recovers automatically or pages
someone at 3am.

**Adding NLP parsing is not the same as adding communication governance.** A regex that extracts
"approve" from a string is parsing. A template registry that maps "I approved this architecture
because it handles failover" to `{decision: approve, subject: architecture, rationale: "handles
failover"}` with typed constraints, NLP tolerance, and schema publication is governance. The
extraction is deterministic, the fields are typed, the schema is published, and the result feeds
directly into the enforcement pipeline. The same distinction applies: status values are surface;
semantics are the point.

**The trust scoring argument.** If an agent can mark work complete without formally asserting "I,
the named obligor, fulfilled what was asked of me," then counting completion rates for trust
scoring is meaningless — you are scoring closure of work records, not quality of fulfilled
obligations. With a commitment, DONE is an accountable assertion to a named party. That assertion
is attached to the agent identity, timestamped, ledgered, and carries weight in the Bayesian
trust model. An agent that marks work done without doing it will eventually be caught — a
FLAGGED attestation decreases their trust score. Routing shifts accordingly. No human configures
this. It emerges from the accountability record.

**The delegation argument.** When Agent A hands work to Agent B who hands it to Agent C, the
delegation chain is a sequence of formally recorded HANDOFF acts, each with a named obligor at
each step, each causally linked by `causedByEntryId`. Six months later, when accountability is
demanded, the chain is in the ledger. With status fields, Agent A re-assigned a record to
Agent B. The accountability question — who was responsible at each point — has no clean answer,
because accountability is structural in the normative layer and inferred (imperfectly) from
status fields.

**Adding status fields is not the same as adding accountability.** The state values are the
surface. The accountability semantics — who promised what to whom, under what authorisation,
with what formal consequence on failure — are what the normative layer provides. The values are
incidental. The semantics are the point.

---

## Why Not a Workflow Engine?

A workflow engine (Temporal, Camunda, BPMN) handles predetermined paths. You define the process
upfront — step A then step B, branch on condition C, retry on failure D. The engine executes
that definition.

The normative layer is not a workflow engine. It is coordination infrastructure for agents whose
outputs determine what happens next — not a configuration file written before execution.

In the insurance claim scenario, the compliance check DECLINES because Lloyd's syndicate
approval is missing. A workflow engine would need a branch defined for this case upfront. The
normative layer handles it because DECLINE is a first-class speech act that the coordinator can
reason about at runtime. A new agent — one not in the original design — can join the channel,
see the DECLINED commitment, and offer its capabilities. The coordination structure emerges from
what agents do, not from what an engineer anticipated.

The composition model deepens this distinction. A workflow engine requires the vocabulary, the
participants, and the decision criteria to be defined upfront. Communication governance allows
the vocabulary to be defined declaratively and matched at runtime — agents discover what
governance acts a protocol expects from published schemas, not from workflow step definitions.
Protocol enforcement allows the decision criteria to evolve without redeploying the workflow —
add a new protocol to a channel, change the enforcement mode, adjust the participant list. The
governance structure adapts because it is infrastructure, not configuration frozen at deployment
time.

More precisely: workflow engines are the right tool when the process is known. The normative
layer is the right infrastructure when autonomous agents are discovering the process. The two are
not competing — a CaseHub case can use Quarkus Flow (a workflow worker) for the steps it knows
upfront, and Qhorus channels for the coordination that emerges between agents. Workflow for the
determined; normative layer for the adaptive.

---

## What This Enables in Practice

The normative layer changes the answers to eight questions that matter in every serious agentic
deployment. Without it, these questions either have no clean answer, or require hours of
archaeology across logs that were never designed to capture what was promised. With it, each is
a query.

### *"Who approved this decision, and what checks ran first?"* — Compliance audit

The FCA asks: "On 14 April, who approved the £180,000 payout and what checks were run before
that decision?" Without the normative layer, a developer manually reconstructs this from
disparate logs across multiple services — a process that takes hours and produces a narrative
that might be wrong. With the normative layer, a single `get_obligation_chain` call on the
payments channel returns the complete obligation history: sanctions screening (DONE), fraud
scoring (DONE, risk 0.12, ENDORSED by compliance officer), compliance check (DECLINED then
re-approved after HANDOFF to senior adjuster), payment attempt (FAILURE), CHAPS retry (DONE) —
with timestamps, agent identities, and causal links.

With communication governance active, the approval is not an inference from the message
content — it is a mechanical extraction with typed fields:
`{decision: "approve", subject: "payout", rationale: "syndicate reference LLY-2026-04-789"}`.
The auditor sees structured evidence, not prose to interpret.

The ledger IS the audit trail. It was built as the system ran, not assembled afterwards. The
SHA-256 hash chain provides integrity detection — any external modification is cryptographically
detectable. For environments requiring protection against insider tampering, optional Ed25519
checkpoint publishing to an external transparency log removes this trust assumption entirely.

### *"What goes in the regulatory report, and how do we know it's complete?"* — Regulatory evidence

In regulated industries, filing a Solvency II pre-notification or an FCA disclosure is itself
a normative act — a COMMAND issued, a confirmation DONE, a reference number recorded. Without
the normative layer, regulatory reports are generated after the fact by extracting data from
disparate systems, assembling a narrative, and hoping nothing was missed. With the normative
layer, `list_ledger_entries` with `type_filter=COMMAND,DONE` on the compliance channel *is*
the regulatory evidence. Every pre-notification, every post-settlement report, every compliance
check is already in the ledger — complete, causally linked to the obligation that required it,
and tamper-evidently sealed. There is no separate reporting system. The process and the proof
are the same record.

### *"Which agent made the decision that caused this, and why?"* — Incident investigation

A payment processed incorrectly. A claim was declined that should have been approved. An
escalation never reached the right agent. Without the normative layer, answering these questions
means hours of distributed trace correlation across multiple services — and the answer, when it
arrives, is a reconstruction that cannot be independently verified. With the normative layer,
`get_causal_chain` on the failing ledger entry walks backwards through every HANDOFF and COMMAND
in the obligation's history in seconds. You see exactly where the chain diverged: which agent
held the obligation, what it decided, whether it was attested by a peer, and what happened next.
The answer is in the ledger, causally linked, and it was put there as the system ran.

### *"Which obligations are overdue, and how long have they been waiting?"* — SLA enforcement

Agents issue COMMANDs and do not always receive responses. In a conventional system, timeout
logic is bespoke — written per workflow, hard to standardise, easy to get wrong. With the
normative layer, `list_stalled_obligations` is a single query that surfaces every COMMAND with
no DONE, FAILURE, DECLINE, or HANDOFF, with the exact duration since it was issued. The Watchdog
module acts on this automatically. There is no polling, no timeout counter, no custom monitoring
code per workflow. Stalled obligations surface themselves because every COMMAND that was issued
is in the ledger, and any that was not resolved is trivially queryable.

### *"When a task was delegated and then failed, who is responsible?"* — Accountability under delegation

An orchestrator issues a COMMAND. Agent A says "not my domain" and HANDOFFs to Agent B. Agent B
fails. Without formal delegation tracking, responsibility is ambiguous — and in regulated
environments, ambiguity is not a process problem, it is a compliance failure. GDPR Article 22
and FCA PS20/1 both require clear attribution of automated decisions. With HANDOFF as a
first-class speech act recorded in the ledger, attribution is a structural property: Agent A
transferred the obligation at timestamp T, Agent B accepted it at T+4s, Agent B failed it at
T+47s with a stated reason. The causal chain is in the ledger, linked by `causedByEntryId`,
and it was built by the infrastructure — not by a human writing an incident report after the
fact.

### *"What did that agent actually do, and should we trust its output?"* — Debugging and observability

An agent produced the wrong output. Without the normative layer, reconstructing what it did
means correlating distributed traces across services, reading logs that were written for
operators rather than auditors, and making inferences about what happened from indirect evidence.
With the normative layer, `get_agent_history` returns every obligation the agent was party to,
in sequence — every COMMAND it received, every DONE or FAILURE it issued, every DECLINE or
HANDOFF it made. `summarise_telemetry` shows every tool it invoked, in order, with wall-clock
duration and token consumption. The complete reasoning trace is in the ledger. It required no
extra instrumentation. It was built as the system ran.

### *"Which agent should handle this task, given everything we know about it?"* — Trust derived from behaviour

The hardest operational question in a large agent mesh is not capability — capability is
declared. It is trust: of all capable agents, which has earned the right to handle this
particular task, given its track record and how its peers have rated its decisions?

In most agentic systems, trust is either hardcoded ("this agent is permitted to do X") or
absent ("we assume all agents are trustworthy"). Neither holds in production. The normative
layer, backed by casehub-ledger, provides a third option: trust derived from the immutable
ledger record of what agents have actually done.

**Attestations** are peer review verdicts stamped onto ledger entries. When an agent reviews
another's decision — a fraud score, a compliance ruling, a payment authorisation — it stamps a
`LedgerAttestation`. Attestations can also be written automatically by the framework when
commitment outcomes carry unambiguous trust signals: a FULFILLED commitment on a COMMAND writes
a SOUND attestation; a FAILURE writes a FLAGGED attestation. The feedback loop from obligation
outcome to trust score is closed without requiring explicit peer review for every interaction.
Explicit attestations carry the values `SOUND`, `ENDORSED`, `FLAGGED`, or `CHALLENGED`, with
evidence text and a confidence score. These are not opinions in application state. They are
immutable records in the same tamper-evident ledger that holds every obligation.

**Bayesian Beta trust scoring** computes a per-actor score from direct attestation history.
Alpha accumulates positive evidence; beta accumulates negative evidence. As peers endorse or
challenge decisions, the distribution narrows and the score stabilises. An agent consistently
endorsed over hundreds of decisions has a fundamentally different score — and a fundamentally
different operational role — from one that has been challenged or has never been attested at all.
The score is a property of the ledger record, not of any configuration file.

**EigenTrust** propagates trust transitively through the mesh via power iteration (Kamvar et
al., 2003). If agent A has attested positively to B's decisions, and B has attested positively
to C's, A has a derived signal about C without ever interacting with it directly. The result is
a global trust share for every actor — a single number in [0.0, 1.0] reflecting standing across
the entire observed network of peer reviews, not just direct relationships.

**Discovery provenance** extends this to how agents came to exist in the mesh. CaseHub applies
the same framework to worker registration (ADR-0006): a worker's registration is a normative
act, recorded in the ledger with the same `causedByEntryId` causal chain as obligation lineage.
A worker introduced by a high-trust provisioner inherits a stronger initial deontic standing via
EigenTrust propagation. Provenance is not a label someone attached — it is a chain in the
ledger, independently verifiable.

**Structural conformance as a trust signal.** When communication governance is active, the
template registry produces a stream of match/no-match outcomes for each agent's governance acts.
An agent that consistently produces structurally matched votes, approvals, and escalations —
conforming to published schemas — demonstrates a different quality of governance participation
than one that frequently triggers Unmatched results or requires NearMiss-guided rephrasing. This
structural conformance record is a natural input to the trust model: not "did the agent fulfill
its obligation?" (the commitment question) but "did the agent communicate its governance acts
in a form the infrastructure could mechanically verify?" (the perception question). Both matter.

The result: who to trust is derived from what agents have done and how their peers have judged
it. Trust accretes from behaviour. It propagates through relationships. It is anchored to
provenance. And it is queryable — the same infrastructure that surfaces obligation health
surfaces trust scores.

### *"Does what the agent said match what it claims to have done?"* — Speech act verification

An agent sends `type: DONE` with content "I need more time to investigate." That is structurally
a STATUS, not a completion. Without communication governance, the normative layer accepts the
declared type at face value — the CommitmentStore moves to FULFILLED, the attestation fires, and
the trust score records a successful completion that did not happen.

With communication governance active, the template registry evaluates the content against the
declared type. A DONE template expects completion language — "completed", "finished", "results
are" — not continuation language. The mismatch produces a `DispatchAdvisory` with source
`normative:speech-act-verification`. In ADVISORY mode, the message dispatches with a warning.
In BLOCKING mode, it is rejected and the agent receives guidance on what a DONE should contain.

This is not content moderation. It is structural verification that the performative force of
the message — what it *does* by being uttered — matches its declared type. The same formal
distinction that makes DECLINE categorically different from FAILURE at the type level extends
to the content level through template matching.

### The core shift

In a system without the normative layer, obligations live in workflow state that is hard to
query and impossible to audit across agent boundaries. Governance acts are prose strings that
humans must interpret. Protocol rules exist in documentation. Trust is configuration. Delegation
trails go cold. Accountability is ambiguous by design.

With the normative layer, every commitment is infrastructure. Every governance act is a
structured extraction. Every protocol rule is enforced at dispatch time. Every delegation is
traceable. Every failure has a stated reason, formally attributed. Every agent's standing is
derived from what it has done and how it has been judged. Regulatory evidence is built as the
system runs. Trust is computed from behaviour, not asserted by configuration.

**The LLM reasons. The infrastructure perceives, enforces, records, and derives.** That is not
a technical detail. It is the difference between an AI system that is capable and one that is
*accountable* — and in any environment where accountability matters, only the latter is fit for
purpose.

The `ChannelGateway` preserves all normative guarantees regardless of backend transport. Every
message — whether originating from an agent via MCP or from a human replying via WhatsApp or
Slack — flows through `MessageService` and `LedgerWriteService`. External backends receive
post-persistence fan-out only; they cannot bypass the normative pipeline.

---

## Grounded in Real Scenarios

The best way to understand the normative layer is to watch it work. Three scenarios demonstrate
the capabilities individually and in composition: an insurance claim (regulated industry,
normative accountability), a production incident (software engineering, every speech act under
pressure), and a governance protocol (communication governance + enforcement, the full
composition loop).

### Scenario 1 — Insurance Claim Processing

A £180,000 commercial fire damage claim processed by a multi-agent AI system — using every
capability the normative layer provides. It is also the reference example for the Quarkus Native
AI Agent Ecosystem: the same claim scenario is demonstrated at each layer of the stack, with each
library adding genuine capability to the same story.

**The agents and what they do**

Nine agents collaborate to process the claim:

| Agent | Role |
|---|---|
| `claims-coordinator` | Orchestrates the full flow |
| `policy-validator` | Checks coverage terms and exclusions |
| `sanctions-screener` | Screens claimant against OFAC/HMT lists |
| `fraud-detection` | Runs ML-based fraud scoring |
| `damage-assessor` | Evaluates damage and estimates value |
| `compliance-officer` | Verifies FCA regulatory requirements |
| `senior-adjuster` | Approves payouts above £100,000 (Lloyd's threshold) |
| `payment-processor` | Handles BACS and CHAPS disbursements |
| `regulatory-reporter` | Files Solvency II pre- and post-settlement reports |

Four channels carry the communication: `claim-456` (main flow), `high-value-review`
(escalation), `compliance-checks` (regulatory), `payments`.

**The claim, step by step**

**Step 1 — Is the policy valid?**
The coordinator sends a QUERY: *"Is policy UK-2024-789 active and does it cover commercial fire
damage?"* The policy-validator responds: *"Active, fire covered, maximum payout £500,000."*

A QUERY is a speech act that creates an expectation. The RESPONSE discharges it. No obligation
was created — this is an information exchange, not a commitment. The 10-type taxonomy
distinguishes QUERY from COMMAND precisely because asking a question and demanding an action are
categorically different acts with different consequences.

---

**Step 2 — Sanctions screening**
COMMAND: *"Screen Acme Corp against OFAC and HMT sanctions lists."* The sanctions-screener
sends STATUS: *"Screening in progress."* Then DONE: *"No matches found."*

COMMAND creates an obligation in the CommitmentStore: the sanctions-screener now owes a result.
STATUS extends the deadline window — the obligation is live. DONE fulfills it and closes the
commitment. If the sanctions-screener had gone silent, the CommitmentStore would show OPEN past
the deadline, and `list_stalled_obligations` would surface it automatically. Communication
governance is not applied here — template matching is selective, used where content-level
extraction adds governance value, not on every message in every channel.

---

**Step 3 — Fraud detection with telemetry**
COMMAND: *"Score this claim for fraud risk."* The fraud-detection agent invokes its ML model,
emits an EVENT — tool `ml-fraud-score`, duration 2,341ms, 1,200 tokens — then DONE: *"Risk
score: LOW (0.12)."*

The COMMAND created an obligation. The EVENT is a telemetry speech act — it carries
observability data without creating a new obligation. `summarise_telemetry` can later aggregate
all EVENT entries to show model performance, token consumption, and latency across the full
claim. The normative ledger records both the DONE (the fulfillment) and the EVENT (the evidence)
as immutable entries, causally linked.

---

**Step 4 — The external surveyor who never responds**
COMMAND: *"Assess fire damage at 42 Industrial Way — dispatch a surveyor."* The damage-assessor
sends STATUS: *"Surveyor dispatched."* Then silence.

The obligation remains OPEN past its deadline — state EXPIRED in the CommitmentStore.
`list_stalled_obligations` returns this COMMAND immediately, flagging it as stalled for 45+
seconds. The normative ledger holds the permanent record: a COMMAND was issued, a STATUS was
reported, and no terminal resolution ever arrived. No custom timeout code. No polling loop. The
infrastructure surfaces the failure.

---

**Step 5 — A QUERY bridges the gap**
Since the surveyor is unresponsive, the coordinator asks: *"Can you estimate the damage without a
site visit?"* QUERY → RESPONSE: *"Based on photos provided: estimated £180,000."*

---

**Step 6 — The compliance check that fails**
COMMAND: *"Verify this claim meets FCA disclosure requirements for commercial fire payouts."*
DECLINE: *"Non-compliant. This claim exceeds £100,000 — Lloyd's syndicate approval is required
before settlement. Approval not on file."*

A DECLINE is not an error. It is a first-class speech act — a formal refusal with a stated
reason. The CommitmentStore moves to DECLINED. The normative ledger records the reason verbatim.
With communication governance active, the template registry extracts the structured refusal:
`{condition: "Lloyd's syndicate approval", status: "not on file", threshold: "£100,000"}` —
turning a prose refusal into actionable fields that the coordinator can route on without parsing
free text.

---

**Step 7 — Escalation via HANDOFF**
COMMAND to compliance-officer: *"Request Lloyd's syndicate approval for a £180,000 commercial
payout."* The compliance-officer sends HANDOFF to senior-adjuster: *"Exceeds my authority — I
am transferring this obligation to the senior adjuster."* The senior-adjuster sends STATUS:
*"Consulting syndicate."* Then DONE: *"Syndicate approval granted. Reference: LLY-2026-04-789."*

The HANDOFF is defeasible reasoning in action: the original obligation is not abandoned, it is
formally transferred. The template registry extracts the delegation:
`{target: "senior adjuster", reason: "exceeds authority"}`. The CommitmentStore moves to
DELEGATED, then — when the senior-adjuster resolves it — to FULFILLED. The normative ledger
records three entries, each linked by `causedByEntryId`:
```
seq=1  COMMAND → causedByEntryId: null           (coordinator issued it)
seq=2  HANDOFF → causedByEntryId: <seq=1 id>     (compliance-officer transferred it)
seq=3  DONE    → causedByEntryId: <seq=2 id>     (senior-adjuster resolved it)
```
`get_causal_chain` on the DONE entry returns this complete ancestry. This is the proof chain a
regulator would ask for: who was responsible, when the obligation transferred, and who discharged
it.

---

**Steps 8–9 — Re-verification and regulatory filing**
With syndicate approval in hand, compliance re-verifies (DONE). The regulatory-reporter files
a Solvency II pre-notification — COMMAND, then an EVENT (external API call, 450ms), then DONE.
Reference `FCA-2026-04-001` recorded in the DONE entry's content field — and with communication
governance, extracted as a structured reference:
`{report_type: "pre-notification", reference: "FCA-2026-04-001"}`.

---

**Step 10 — Payment failure and recovery**
COMMAND: *"Process BACS payout of £180,000 to Acme Corp."* FAILURE: *"BACS validation failed —
sort code 20-14-09 is not registered."*

Then: COMMAND: *"Retry via CHAPS."* STATUS: *"CHAPS processing."* DONE: *"Payment confirmed.
Reference: CHAPS-2026-04-001."*

Two obligations: one FAILED, one FULFILLED. The template registry extracts the failure evidence:
`{method: "BACS", error: "sort code not registered", sort_code: "20-14-09"}` — structured fields
that feed directly into incident analysis without log parsing.

---

**Steps 12–13 — Regulatory close-out and audit seal**
Post-settlement Solvency II report filed (DONE). Audit record sealed (DONE).

The final state: the normative ledger holds 18 entries across four channels, causally linked,
SHA-256 hash-chained, tamper-evident. Every COMMAND has a terminal resolution. The one stalled
obligation (the surveyor) is permanently recorded as unresolved — not deleted, not hidden,
permanently visible as a gap in the audit trail.

---

**What the dashboard shows**

The Agent Mesh Dashboard renders this state in real time:

```
+==============================================================================+
|  Acme Corp -- Fire Damage Claim #456  [s: next  r: reset  q: quit]           |
+==============================================================================+
|  CHANNEL OBLIGATION HEALTH                                                   |
|  Channel            | Commands | Done | Failed | Declined | Stalled          |
|  -------------------+----------+------+--------+----------+--------          |
|  claim-456          |    7     |  4   |   0    |    0     |  1 [!]           |
|  compliance-checks  |    3     |  3   |   0    |    1     |  0 [ok]          |
|  high-value-review  |    1     |  1   |   0    |    0     |  0 [ok]          |
|  payments           |    2     |  1   |   1    |    0     |  0 [!]           |
+==============================================================================+
|  RECENT OBLIGATIONS                                                          |
|  corr-pay-2   COMMAND -> DONE     payment-processor   CHAPS confirmed  [ok]  |
|  corr-pay-1   COMMAND -> FAILURE  payment-processor   Invalid sort code [x]  |
|  corr-solvii  COMMAND -> DONE     regulatory-reporter Pre-notification  [ok] |
|  corr-comp-2  COMMAND -> DONE     compliance-officer  FCA re-verified   [ok] |
|  corr-comp-1  COMMAND -> DECLINE  compliance-officer  Missing syndicate [o]  |
|  corr-surv    COMMAND -> STALLED  damage-assessor     Awaiting surveyor[~~]  |
+==============================================================================+
```

---

### Scenario 2 — A Production Database Corruption (Software Engineering)

The insurance scenario demonstrates the normative layer in a regulated financial context. This second scenario demonstrates the same layer in a software engineering context — chosen because every speech act type becomes load-bearing under time pressure, and every distinction in the commitment lifecycle has immediate operational consequence. It is also a scenario every engineer has lived through.

**The incident:** A migration script deployed to production corrupts user records. Five agents must coordinate to diagnose, recover, and notify — under a 30-minute P0 SLA, with the deployment window closing.

| Agent | Role |
|---|---|
| `detection` | Monitors error rates, opens the case |
| `diagnosis` | Investigates root cause — needs DB access |
| `rollback` | Attempts to reverse the deployment |
| `recovery` | Recovers data from backup if rollback fails |
| `communication` | Notifies affected users once scope is known |

**Why every speech act type appears:**

QUERY: Before issuing a COMMAND to diagnose the corruption, the detection agent queries: *"Who has production DB read access?"* — capability negotiation before an obligation is created.

COMMAND + ACKNOWLEDGED: The diagnosis agent receives a COMMAND and ACKNOWLEDGES it — confirming active acceptance, not just queuing.

STATUS: The rollback agent sends STATUS updates every 30 seconds: *"Schema rollback in progress — 3 of 7 tables complete."* These are assertive speech acts — truthful reports of current state — not state transitions.

DECLINE: The communication agent receives a COMMAND to notify users but cannot act yet — the scope of affected records is unknown. It sends DECLINE with reason: *"Scope not yet confirmed — will re-engage when diagnosis is complete."* With communication governance, the template extracts the structured dependency: `{wait_condition: "scope confirmed", dependency: "diagnosis"}` — the coordinator knows exactly what the communication agent is waiting for and can re-issue the COMMAND when the condition is met.

FAILURE: The rollback agent attempts schema reversal and discovers the corruption is not reversible — the migration modified records, not only schema. It sends FAILURE: *"Schema rollback not sufficient — data changes are irreversible. Escalating to recovery path."* The template extracts: `{attempted: "schema rollback", cause: "data changes irreversible", escalation: "recovery path"}`.

HANDOFF: The rollback agent formally transfers the obligation to the recovery agent: *"Transferring recovery obligation — backup from 02:00 contains clean records."* The template extracts: `{target: "recovery", evidence: "backup from 02:00"}`.

DONE: The recovery agent completes the work. Trust scoring fires: rollback FAILED (review its reliability for irreversible operations), recovery FULFILLED (reinforce for data recovery tasks).

EXPIRED: At the 30-minute mark, the user notification SLA expires. The communication agent's commitment — which was DECLINED pending scope confirmation — transitions to EXPIRED. This is not a failure of the communication agent; it is a systemic SLA breach caused by the extended diagnosis time.

**What the supervisor sees in one query:**

```
list_stalled_obligations(channelName: "case-p0-db-corruption/coordination")

→ corr-comms    COMMAND → EXPIRED      communication    Notification SLA breached [30min]
→ corr-rollback COMMAND → FAILURE      rollback         Data irreversible, escalated [22min]
```

Two lines. The supervisor knows: the rollback path was exhausted and correctly escalated; the notification SLA has breached. Neither requires reading a transcript or correlating logs.

**Without the normative layer**, the rollback FAILURE and the communication DECLINE both present as "agent did not complete assigned work." The correct response to FAILURE (activate the recovery path immediately) and the correct response to DECLINE (wait for scope, then re-issue COMMAND) are different. During a live P0, treating them identically costs time — measured in affected users and revenue.

This scenario is the basis of a structured empirical test comparing the normative layer against LangChain4j coordination: [engine#189](https://github.com/casehubio/engine/issues/189).

---

### Scenario 3 — Governed Architecture Review (Full Composition)

The first two scenarios demonstrate normative accountability — commitment lifecycle, ledger,
trust scoring — with communication governance adding structured extraction to key steps. This
third scenario demonstrates the full composition: all three capabilities load-bearing
simultaneously, producing an outcome that none could produce alone.

**The situation:** Three agents — `architect`, `security-lead`, and `tech-lead` — must review
and vote on a proposed architecture change. The vote is governed: all three must participate,
each must cast a structurally valid vote, and the outcome is determined by the extracted fields,
not by counting messages.

**The governance vocabulary:**

```yaml
namespace: protocol:review
templates:
  vote:
    pattern: "I ${decision} this ${subject}[ because ${rationale}]"
    variables:
      decision: { type: enum, values: [approve, reject, abstain] }
      subject: { type: string }
      rationale: { type: string, optional: true }
  escalate:
    pattern: "Escalating to ${target}[ due to ${reason}]"
    variables:
      target: { type: string }
      reason: { type: string, optional: true }
```

**The channel configuration:**

```
channel: architecture-review
protocols: [CONTRIBUTION_REQUIRED, protocol:review]
protocolParticipants: [architect, security-lead, tech-lead]
enforcementMode: BLOCKING
```

**The review, step by step:**

**Step 1 — Schema publication.** The template registry publishes the sentence schemas for
`protocol:review` to the channel. Each agent sees the expected patterns and variable constraints
before producing its vote. Schema publication is the perception layer's answer to the conformance
question: tell agents what shape to produce, then extract mechanically.

**Step 2 — The first vote.**
The architect writes: *"I approved this service mesh migration because the latency improvements
justify the operational complexity."*

The template registry matches this against the `vote` template. The NLP pipeline handles
"approved" (past tense → lemma "approve") and produces a structurally matched result:
`{template: "vote", decision: "approve", subject: "service mesh migration",
rationale: "the latency improvements justify the operational complexity"}`.

The enforcement pipeline evaluates: `CONTRIBUTION_REQUIRED` checks participation — 1 of 3
participants have voted. The message dispatches. The normative ledger records the vote with
the extracted fields.

**Step 3 — The dissenting vote.**
The security-lead writes: *"I reject this proposal because it exposes internal services to the
mesh control plane without mTLS by default."*

Template extraction: `{decision: "reject", subject: "proposal",
rationale: "it exposes internal services to the mesh control plane without mTLS by default"}`.
Participation: 2 of 3. The message dispatches.

**Step 4 — An unmatched attempt.**
The tech-lead writes: *"Looks good to me, let's go ahead."*

The template registry returns `Unmatched` — this prose does not match the `vote` template
(no "I ${decision} this ${subject}" structure). `BLOCKING` enforcement rejects the message.
With NearMiss feedback active, the rejection advisory includes the expected pattern — guided
rejection rather than silent failure. The tech-lead sees what shape the protocol expects and
rephrases.

**Step 5 — The conformant vote.**
The tech-lead writes: *"I approve this migration."*

Template extraction: `{decision: "approve", subject: "migration"}`. Participation: 3 of 3.
The `CONTRIBUTION_REQUIRED` protocol confirms all participants have cast structurally matched
votes. The handshake gate opens.

**Step 6 — The governed outcome.**
The protocol evaluates the extracted fields across all three votes:
- architect: approve
- security-lead: reject
- tech-lead: approve

Result: approved 2-1. The outcome is determined by the extracted `decision` fields — not by
counting messages, not by parsing prose, not by asking another LLM to interpret the conversation.
The enforcement pipeline records the outcome as an EVENT with the full vote tally.

**Step 7 — Bidirectional generation.**
The infrastructure generates a governance summary from the extracted fields:
*"The service mesh migration was approved by architect and tech-lead (2-1). security-lead voted
reject, citing exposure of internal services without mTLS."*

This summary is generated from structured data, not summarised from prose. It is deterministic,
auditable, and guaranteed to reflect the actual votes — because it was produced from the same
extracted fields that the enforcement pipeline evaluated.

**What made this possible:**

Without communication governance, the protocol can only count messages — not evaluate what was
decided. "Looks good to me" counts as a vote. Without protocol enforcement, the approval has
no handshake gate — it takes effect without confirming all participants weighed in. Without
normative accountability, the vote tally has no ledger trail — no causal chain, no attestation,
no trust signal.

With all three: prose is parsed into typed governance acts. The protocol gates the outcome on
participant agreement over the extracted fields. The normative ledger records every vote, the
collective outcome, and the causal chain — permanently, tamper-evidently, and queryable.

**This is the full composition:** perception + enforcement + accountability = governed
accountability from natural language.

---

### The layered ecosystem

The claim scenario is the reference example across the full Quarkus Native AI Agent Ecosystem:

| Layer | Library added | What changes in the scenario |
|---|---|---|
| 1 | casehub-qhorus + casehub-ledger | Synthetic agents, keyboard-driven, full obligation trail |
| 2 | + casehub-work | Claims become real work items with inboxes; agents pick up tasks from queues |
| 3 | + claudony | Agents are real LLM instances; coordinator reasons about which agent to assign |
| 4 | + casehub | Claims are real cases from the CaseHub engine; full production stack |

Each layer adds genuine capability to the same claim. The dashboard structure, the obligation
board builders, and the ledger query tools carry through unchanged. The scenario driver is the
only thing replaced at each layer — by an `@Alternative` CDI bean that connects to the layer's
own data source. Extension, not duplication.

The composition dimensions are orthogonal to this layered stack:

| Composition | Without | With |
|---|---|---|
| Communication governance | Governance acts are prose strings | Governance acts are structured extractions |
| Protocol enforcement | Rules are ad-hoc, metadata-only | Rules are YAML-declared, content-aware, handshake-gated |
| Full composition | Message types tracked, prose ungoverned | Full loop: prose → extraction → enforcement → accountability |

---

## Related Documents

| Document | What it covers |
|----------|---------------|
| [normative-framework.md](normative-framework.md) | Entry point and navigation for this body of works |
| [agent-mesh-framework.md](agent-mesh-framework.md) | Developer guide — message vocabulary, channel topology, template-based governance |
| [multi-agent-framework-comparison.md](multi-agent-framework-comparison.md) | Feature-level comparison of Qhorus against AutoGen, LangGraph, CrewAI, Gastown, and others |
| [agent-protocol-comparison.md](agent-protocol-comparison.md) | How Qhorus, A2A, and ACP are complementary rather than competing |
| [normative-summary.md](normative-summary.md) | Critique, gaps, and what needs strengthening |

*Quarkus Qhorus — the agent communication mesh for the Quarkus Native AI Agent Ecosystem.*
*Theoretical foundation documented in ADR-0005. Empirical hypothesis at [engine#189](https://github.com/casehubio/engine/issues/189).*
*Platform context: [CaseHub Platform](https://github.com/casehubio/parent/blob/main/docs/PLATFORM.md).*
