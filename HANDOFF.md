# CaseHub Qhorus — Session Handover
**Date:** 2026-05-01 — Normative doc review, cross-channel correlation documented, platform conventions

---

## What Was Done This Session

- **normative-summary.md** — new `docs/normative-summary.md`: reading guide, layering analysis, and critique of five gaps across the three normative documents
- **Cross-channel causal correlation documented as implemented** — `agent-mesh-framework.md` updated: `causedByEntryId` documented as spanning channels; `get_obligation_activity` documented as walking the causal DAG (not just correlationId join); `get_causal_chain` no longer takes `channel_name`. Implementation must match this before the docs go public.
- **Platform conventions** — two new files in `casehubio/parent/docs/conventions/`: `qhorus-event-content-null.md` (EVENT content always null, render telemetry fields) and `qhorus-human-governance-channel-types.md` (oversight channel must have `allowedTypes=QUERY,COMMAND`). Both indexed.
- **Build confirmed** — 970 tests, 0 failures

## Current State

- **Branch:** `main` — clean, pushed (qhorus); parent conventions committed locally
- **Open issues:** #131 (channel backend abstraction), #132 (delivery guarantees), #124 (InstanceActorIdProvider), #98 (accuracy baseline)

## Immediate Next Step

**Implement cross-channel causal correlation** — the docs describe it as working. Key changes needed:
1. `causedByEntryId` resolution must query across all channels (currently scoped to one channel)
2. `get_obligation_activity` must walk `causedByEntryId` links across channel boundaries
3. `get_causal_chain` signature drops `channel_name` parameter

Then **#124** — `InstanceActorIdProvider` SPI for Claudony.

## Key Architecture Facts

*Unchanged — `git show HEAD~2:HANDOFF.md`*

## References

| What | Path |
|---|---|
| Normative summary + critique | `docs/normative-summary.md` |
| Cross-channel correlation docs | `docs/agent-mesh-framework.md` Part 6 |
| Platform conventions | `casehubio/parent/docs/conventions/qhorus-event-content-null.md` |
| Blog entries | `blog/2026-05-01-mdp04-the-body-of-work.md` |
| Previous handover | `git show HEAD~1:HANDOFF.md` |
