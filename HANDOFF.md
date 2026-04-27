# Quarkus Qhorus — Session Handover
**Date:** 2026-04-27 — normative ledger + MCP governance session

---

## What Was Done This Session

- **Normative ledger complete** — `MessageLedgerEntry` records all 9 types. `LedgerWriteService.record()` called unconditionally for every `sendMessage`. `list_ledger_entries` replaces `list_events`. Causal chain via `causedByEntryId`. Reactive stack mirrored. Old `AgentMessageLedgerEntry` deleted. 783 tests, 0 failures.
- **Epic #99 closed** (#100–#109 closed). All work merged to main.
- **MCP consistency pass closed (#120)** — `channel_digest`→`get_channel_digest`, `timeout_s`→`timeout_seconds`, `agent_id`→`sender` in `list_ledger_entries`, `get_replies` gained pagination. **Claudony needs one doc update:** `claudony-agent-mesh-framework.md` line 705 — `agent_id?`→`sender?`.
- **`docs/normative-layer.md` published** — four-layer methodology framing, EigenTrust/Bayesian Beta trust models, insurance claim grounded scenario, governance not middleware positioning.
- **All specs updated** — trust models, CaseHub ADR-0006 cross-references, methodology framing across all three design specs.
- **CLAUDE.md updated** — Platform Coherence Protocol noted (parent Claude added URLs; they 404 until casehub-parent publishes docs); ledger package structure corrected; PendingReplyStore removed.

## Current State

- **Branch:** `main` — everything merged and pushed
- **Open epics:** #110 (Ledger Query Capabilities — 6 tools, specced, not implemented), #119 (MCP consistency — #120 closed, #121 for-review open)
- **`#98` parked** — classification accuracy baseline; run when stepping away from laptop
- **Jlama PRs** — submitted upstream, waiting on quarkiverse reviewers

## Immediate Next Step

Implement **Epic #110** — 6 new ledger query tools + 6 repository methods:
- Tools: `get_obligation_chain`, `get_causal_chain`, `list_stalled_obligations`, `get_obligation_stats`, `get_telemetry_summary`, enhanced `list_ledger_entries`
- Spec: `docs/superpowers/specs/2026-04-26-ledger-query-capabilities-design.md` §§3–5, 9, 10
- Issues #111–#118 all created and ready
- **Start with:** `superpowers:writing-plans` against the spec, then subagent-driven-development

## Key Architecture Facts

*Unchanged — `git show HEAD~1:HANDOFF.md`*

Plus new:
- `LedgerWriteService.record()` — all 9 types, unconditional, REQUIRES_NEW
- `MessageLedgerEntryRepository` — `listEntries(channelId, messageTypes?, afterSequence?, sender?, since?, limit)`, `findLatestByCorrelationId` for causal chain resolution
- MCP naming: `get_channel_digest`, `timeout_seconds`, `sender` (not `agent_id`) in `list_ledger_entries`
- Platform Coherence Protocol URLs in CLAUDE.md return 404 — apply spirit manually per inline domain ownership note

## References

| What | Path |
|---|---|
| Ledger query spec | `docs/superpowers/specs/2026-04-26-ledger-query-capabilities-design.md` |
| Normative layer doc | `docs/normative-layer.md` |
| MCP for-review items | GitHub issue #121 |
| Previous handover | `git show HEAD~1:HANDOFF.md` |
