# CaseHub Qhorus — Session Handover
**Date:** 2026-05-01 — capabilityTag on LedgerAttestation, #133 and #134 closed

---

## What Was Done This Session

- **#133 done** — `LedgerWriteService.writeAttestation` now extracts `"capability"` from the COMMAND's content JSON and sets `attestation.capabilityTag`; falls back to `CapabilityTag.GLOBAL`. Also required implementing three new abstract methods added to the `casehub-ledger` runtime interface in SNAPSHOT (`findAttestationsByEntryIdAndCapabilityTag`, `findAttestationsByEntryIdGlobal`, `findAttestationsByAttestorIdAndCapabilityTag`). Two new integration tests in `LedgerAttestationIntegrationTest`.
- **#134 closed** — `get_obligation_activity` was already complete (both stacks, repo method, tests). Closed as stale open issue.
- **Garden** — 2 entries submitted: SNAPSHOT interface contract gotcha (`GE-20260501-a9ea1a`), javap named query technique (`GE-20260501-93f9a8`).
- **Blog** — `blog/2026-05-01-mdp03-scoped-trust.md`

## Current State

- **Branch:** `main` — pushed, clean (`.claude/settings.local.json` modified; `docs/work-and-workitems.md` untracked — intentional work product complementing the normative docs, not yet committed)
- **968 tests, 0 failures**
- **Open issues:** #131 (channel backend abstraction), #132 (delivery guarantees), #124 (InstanceActorIdProvider), #98 (accuracy baseline)

## Immediate Next Step

**#124** — `InstanceActorIdProvider` SPI design. Claudony needs this to map Qhorus `instanceId` → ledger `actorId` (persona format). The `DefaultInstanceActorIdProvider` is a no-op identity; Claudony replaces it with a `@Alternative` that resolves session → persona.

## Key Architecture Facts

*Unchanged — `git show HEAD~2:HANDOFF.md`*

## References

| What | Path |
|---|---|
| Blog entries | `blog/2026-05-01-mdp03-scoped-trust.md` |
| Agent mesh guide | `docs/agent-mesh-framework.md` |
| Normative layer | `docs/normative-layer.md` |
| Previous handover (full context) | `git show HEAD~1:HANDOFF.md` |
