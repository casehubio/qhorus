# Quarkus Qhorus — Session Handover
**Date:** 2026-04-28 — Trust Signals and Typed Channels

---

## What Was Done This Session

- **#122 closed** — NormativeChannelLayout + `MessageTypePolicy` SPI. `allowed_types` on channels (null=open, "EVENT", "QUERY,COMMAND"). Enforced client-side in MCP tools and server-side in `MessageService`. `examples/normative-layout/` added (27 CI tests, no LLM — canonical Layer 1 reference). 1,073 tests.
- **#123 + #124 closed** — Trust signal layer. `CommitmentAttestationPolicy` SPI (DONE→SOUND/0.7, FAILURE/DECLINE→FLAGGED). `InstanceActorIdProvider` SPI (session→persona actorId). Fixed production tx bug: `CommitmentStore` queried inside `REQUIRES_NEW` always sees stale OPEN state — fixed by deriving verdict from `MessageType` directly. 931 tests.
- **quarkus-ledger fixed** — `TrustScoreComputer` was ignoring the `confidence` field entirely. Fixed: `weight = recencyWeight × clamp(confidence, 0, 1)`. DEFAULT 1.0 on column for backward compat. Both repos pushed.
- **#125 + #126 closed** — `delete_channel` (force guard, FK-safe purge), `get_instance`, `get_message`. All three in blocking + reactive tools. 944 tests.
- **#121 decisions recorded** — all 8 items decided. Breaking changes queued: rename to `artefact`, auto-manage claim/release, 3-step chunked upload, `read_only` flag on register, `list_pending_commitments`. Non-breaking items shipped (#125, #126).
- **#127 created** — add `caller_instance_id` + `checkAdminAccess` to `delete_channel`.

## Current State

- **Branch:** `main` — everything merged and pushed
- **Open issues:** #119 (MCP consistency epic), #121 (decisions recorded, breaking implementation pending), #127 (delete_channel admin guard), #98 (accuracy baseline)
- **Breaking changes queued under #121:** A (artefact terminology), C (auto-manage claim/release), D (3-step chunked upload), G (read_only flag on register), H (list_pending_commitments)

## Immediate Next Step

Begin breaking MCP surface redesign. Start with **#121-A** (artefact terminology) — create child issue under #119, rename `share_data`→`share_artefact`, `get_shared_data`→`get_artefact`, `list_shared_data`→`list_artefacts` in both tool classes. Then G (observer consolidation) and H (commitment consolidation).

Alternatively: **#127** (delete_channel admin guard) is small and additive — good warm-up.

## Key Architecture Facts from This Session

- `MessageTypePolicy` + `StoredMessageTypePolicy` enforce `channel.allowedTypes` at MCP tool layer (fail-fast) and `MessageService.send()` (safety net). Same `@DefaultBean`/`@Alternative` pattern as other SPIs.
- `CommitmentAttestationPolicy` + `InstanceActorIdProvider` wired into `LedgerWriteService.record()` — do NOT query `CommitmentStore` inside `REQUIRES_NEW` (outer tx uncommitted, stale reads guaranteed).
- `fk_message_channel` has no CASCADE — `delete_channel` must call `messageStore.deleteAll(channelId)` before `channelStore.delete(channelId)`.
- `MessageStore.countByChannel()` returns `int`, not `long`.

## References

| What | Path |
|---|---|
| Latest blog | `blog/2026-04-28-mdp01-what-the-channel-allows.md` |
| Trust signal layer design | `docs/superpowers/specs/2026-04-28-issue-123-124-trust-signal-layer-design.md` |
| MCP tool gaps design | `docs/superpowers/specs/2026-04-28-issue-125-126-mcp-tool-gaps-design.md` |
| #121 decisions | GitHub issue #121 comment (2026-04-28) |
| Previous handover | `git show HEAD~1:HANDOFF.md` |
