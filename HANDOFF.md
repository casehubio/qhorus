# CaseHub Qhorus — Session Handover
**Date:** 2026-05-01 — Agent mesh docs, cross-channel correlation, @Tool overload fix

---

## What Was Done This Session

- **#129 fixed** — non-`@Tool` public overloads sharing a name with `@Tool` methods cause quarkus-mcp-server to silently drop the tool. Made all conflicting overloads package-private. 53 test files updated to full @Tool signatures. `ToolOverloadDiscoverabilityTest` guards regressions (pure reflection). Examples module also required fixing — only caught by `mvn install` from root, not `mvn test` in runtime.
- **#122 done** — `docs/agent-mesh-framework.md` — comprehensive developer guide: message vocabulary, channel model, NormativeChannelLayout, agent lifecycle, CommitmentStore, ledger (8 query tools), human-in-the-loop, Layer 1 Secure Code Review example, anti-patterns, quick-start template.
- **#134 done** — `get_obligation_activity(correlationId, limit?)` — cross-channel ledger query returning all entries sharing a correlationId, ordered by `messageId ASC` (global), with `channel` field on each entry. Key discoveries: `MessageLedgerEntry.content` is null for EVENT entries (telemetry extracted to dedicated fields); `sequenceNumber` is per-channel not global. Agents must pass `correlationId` on EVENT messages to link them.
- **Platform conventions** — 3 new files + 1 extended in `casehubio/parent/docs/conventions/`. Parent Claude also ran a broader scan — `PENDING-MODULE-UPDATES.md` in parent tracks which module CLAUDE.md files need duplicate content removed.
- **CLAUDE.md** — 2 new conventions: @Tool overload visibility rule; `mvn install` from root required after API visibility changes.

## Current State

- **Branch:** `main` — all committed and clean (`fix_tool_calls.py` leftover at root, can be deleted)
- **968 tests, 0 failures**
- **Open issues:** #131 (channel backend abstraction), #132 (delivery guarantees), #124 (InstanceActorIdProvider), #98 (accuracy baseline)

## Immediate Next Step

Delete `fix_tool_calls.py` from repo root (untracked leftover from bulk test fix). Then pick:
- **#124** — InstanceActorIdProvider SPI design (Claudony needs this for session→persona mapping in the ledger)
- **#98** — accuracy baseline with `-Pwith-llm-examples` (Jlama + model in `~/.jlama/`)
- **Module CLAUDE.md cleanup** — `casehubio/parent/docs/conventions/PENDING-MODULE-UPDATES.md` has the work list

## Key Architecture Facts

*Unchanged — `git show HEAD~2:HANDOFF.md`*

## References

| What | Path |
|---|---|
| Blog entries | `blog/2026-05-01-mdp01-*.md`, `blog/2026-05-01-mdp02-docs-that-build-themselves.md` |
| Agent mesh guide | `docs/agent-mesh-framework.md` |
| Parent conventions pending | `casehubio/parent/docs/conventions/PENDING-MODULE-UPDATES.md` |
| Previous handover (full context) | `git show HEAD~1:HANDOFF.md` |
