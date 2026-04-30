# CaseHub Qhorus — Session Handover
**Date:** 2026-04-30 — Platform-wide breaking change sweep

---

## What Was Done This Session

- **All #121 breaking changes shipped:** artefact rename (A), auto-claim/release (C), 3-step chunked upload (D), observer → read_only flag (G), list_pending_commitments (H). Plus #127 delete_channel admin guard. Plus #128 casehub-qhorus-api module split. Plus casehub-ledger #73 api/jpa split.
- **quarkus-work (#70, #71, #72):** TrustGateService migration, migration non-issue confirmed, JSON/null guard fixes; 13 module directories renamed `quarkus-work-*` → `casehub-work-*`.
- **claudony (#95):** ClaudonyLedgerEventCapture race condition fixed (PESSIMISTIC_WRITE).
- **casehub-engine PR #216:** Import updates to new api package paths — CI green, waiting merge.
- **CI debugging:** GitHub Packages SNAPSHOT timing race; findScore() not committed; transitive SNAPSHOT unreliable — fixed with explicit dep declarations.
- **Platform naming sweep (casehubio/parent#8, closed):** work/README, parent/README, assisteddev/README, qhorus/docs/DESIGN.md, normative-layer.md, adr/0004 all updated. Stale worktrees pruned across 5 repos.
- **CLAUDE.md:** Testing conventions updated — ObserverRegistry removed, read_only/include_events patterns, auto-claim, chunked upload, admin guard notes.

## Current State

- **Branch:** `main` — everything merged and pushed across all repos
- **engine PR #216:** Open, CI green, waiting for review/merge
- **casehub-qhorus-api:** Panache constraint means only enums/SPIs/exceptions moved to api; store interfaces and domain entities remain in runtime
- **Open issues:** #129 (mcp-server @Tool overload drop), #122 (agent mesh docs), #124 (InstanceActorIdProvider design), #98 (accuracy baseline)

## Immediate Next Step

Merge **casehub/engine PR #216**. Then pick any of:
- **#129** — make non-`@Tool` `sendMessage` overloads package-private; verify tools appear in MCP tool list (claudony `McpServerIntegrationTest`)
- **#98** — accuracy baseline with `-Pwith-llm-examples`
- **#122** — agent mesh framework documentation

## Key Architecture Facts

- `casehub-qhorus-api`: enums (ChannelSemantic, MessageType, CommitmentState), SPIs (CommitmentAttestationPolicy, InstanceActorIdProvider, MessageTypePolicy), MessageTypeViolationException. Store interfaces and JPA entities stay in runtime — Panache single-inheritance blocks the full domain split.
- `quarkus-ledger-api`: domain @MappedSuperclass POJOs, repository interfaces, LedgerTraceIdProvider, ActorTypeResolver. JPA entities extend api types — clean split, no Panache conflict.
- Observer model: `register_observer`/`read_observer_events` removed. Use `register(read_only=true)` + `check_messages(include_events=true)`.
- Auto-claim: `send_message` with `artefact_refs` auto-claims for sender (registered instances only). DONE/FAILURE/DECLINE auto-releases. `isGcEligible` false for artefacts on in-flight COMMANDs.
- GitHub Packages does NOT reliably resolve transitive SNAPSHOTs — explicit dep declarations required in consumer poms.

## References

| What | Path |
|---|---|
| Blog entry | `blog/2026-04-30-mdp01-platform-wide-breaking-window.md` |
| Platform naming issue (closed) | `casehubio/parent#8` |
| Engine PR | `casehubio/engine#216` |
| Previous handover | `git show HEAD~1:HANDOFF.md` |
