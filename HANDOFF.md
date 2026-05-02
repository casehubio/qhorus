# CaseHub Qhorus — Session Handover
**Date:** 2026-05-02 — Obligation-continuity framing corrected, all commits pushed

---

## What Was Done This Session

- **normative-summary.md** — new `docs/normative-summary.md`: reading guide, layering analysis, and critique of five gaps across the three normative documents
- **Cross-channel causal correlation documented as implemented** — `agent-mesh-framework.md` updated: `causedByEntryId` documented as spanning channels; `get_obligation_activity` walks the causal DAG (not just correlationId join); `get_causal_chain` drops `channel_name`. Implementation must match this before docs go public.
- **Platform conventions** — two new files in `casehubio/parent/docs/conventions/`: `qhorus-event-content-null.md` and `qhorus-human-governance-channel-types.md`. Pushed to GitHub.
- **Obligation-continuity framing corrected** — `work-and-workitems.md` and `normative-summary.md` updated: the machine/human distinction is continuity, not duration. Added explicit treatment of prerequisite-blocking and priority-deferral: machine agents use DECLINE+reissue, not silent parking. Silent parking = stalled obligation anti-pattern.
- **All commits pushed** — qhorus and parent both current on GitHub.
- **Build confirmed** — 970 tests, 0 failures

## Current State

- **Branch:** `main` — clean, fully pushed
- **Open issues:** #131 (channel backend abstraction), #132 (delivery guarantees), #124 (InstanceActorIdProvider), #98 (accuracy baseline)

## Immediate Next Step

**Implement cross-channel causal correlation** — docs describe it as working, implementation must catch up:
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
| Human-layer framing | `docs/work-and-workitems.md` §The Distinction That Matters |
| Cross-channel correlation docs | `docs/agent-mesh-framework.md` Part 6 |
| Platform conventions | `casehubio/parent/docs/conventions/` |
| Previous handover | `git show HEAD~1:HANDOFF.md` |
