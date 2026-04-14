# Quarkus Qhorus — Session Handover
**Date:** 2026-04-14

## What Was Done This Session

A very long session covering implementation (Phases 2–4), multiple code review rounds, architecture design, and documentation.

**Implementation completed:**
- Phase 2 (MCP tools) — 14 `@Tool` methods, 8 return-type records
- Phase 3 (channel semantics) — LAST_WRITE, EPHEMERAL, COLLECT, BARRIER enforced
- Phase 4 (wait_for_reply) — PendingReply polling, timeout, cleanup job, PendingReplyCleanupJob
- Code reviews across all phases — 129 → 193 tests; 2 critical production bugs fixed:
  - `DataService.claim()` idempotency (double-claim premature GC bug)
  - `wait_for_reply` instance_id — now accepts human-readable agent name, not UUID

**Architecture decisions:**
- Phase 10 `request_approval` moved out of Qhorus → `quarkus-tarkus-qhorus` (new project)
- Phases 10–12 added to roadmap (human-in-the-loop, governance, structured observability)
- Phase 10 narrowed to channel controls only: `pause_channel`, `resume_channel`, force-close, external wait cancel

**New project created:** `~/claude/quarkus-tarkus` — Quarkiverse extension for human-scale WorkItem lifecycle management. Fully scaffolded with CLAUDE.md, design spec, HANDOFF.md. Phase 1 not yet started.

**Documentation added:**
- `docs/qhorus-vs-cross-claude-mcp.md` — why Qhorus over the original
- `docs/agent-protocol-comparison.md` — A2A vs ACP vs Qhorus
- `docs/multi-agent-framework-comparison.md` — 10-framework comparison table (cross-claude-mcp, Qhorus, A2A, ACP, AutoGen, Swarm, LangGraph, Letta, CrewAI, MCP)

## Current State

- **Tests:** 193 passing, 0 failing
- **Last commit:** `dc01262` docs: multi-agent framework comparison table
- **All work committed** — `.claude/settings.local.json` only dirty file (never committed)
- **Open GitHub issues:** none (all closed)

## Immediate Next Steps

**Option A (recommended):** Phase 5 — wire `artefact_refs` on messages into the MCP tool flow
- `Message.artefactRefs` column exists (comma-separated UUID strings) but not used in tools yet
- `send_message` should accept `artefact_refs` param; `MessageSummary` should return them
- Run `issue-workflow` Phase 1 first to create issues

**Option B:** Phase 6 — capability/role addressing in `send_message`
**Option C:** Phase 7 — Agent Card `/.well-known/agent-card.json` (smallest phase)
**Option D:** Phase 10 channel controls — `pause_channel`, `resume_channel`, force-close (independent of Tarkus)

**Also needed:** Update DESIGN.md to mark Phase 4 as Done (still shows pending).

## References

| What | Path |
|---|---|
| Design spec | `docs/specs/2026-04-13-qhorus-design.md` |
| Implementation tracker | `docs/DESIGN.md` |
| Roadmap (phases 1–12) | `docs/DESIGN.md` § Build Roadmap |
| Protocol comparison | `docs/agent-protocol-comparison.md` |
| Framework comparison | `docs/multi-agent-framework-comparison.md` |
| Qhorus vs cross-claude-mcp | `docs/qhorus-vs-cross-claude-mcp.md` |
| Quarkus Tarkus (new project) | `~/claude/quarkus-tarkus/HANDOFF.md` |
| Tarkus plan file | `~/.claude/plans/mellow-puzzling-seal.md` |
| Garden submissions this session | PRs #21–#29, #34–#35 (Hortora/garden) |
