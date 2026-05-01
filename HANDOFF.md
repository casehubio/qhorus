# CaseHub Qhorus — Session Handover
**Date:** 2026-05-01 — Test bug fixes and channel design

---

## What Was Done This Session

- **`WatchdogEnabledTest` flaky fixed** — `@TestTransaction` added to all 6 condition evaluation tests + UUID suffix on e2e channel names. Root cause: `WatchdogScheduler` runs in a separate thread/transaction and can see committed test watchdogs. Commit `b22742a`.
- **`LedgerCaptureExampleTest` always-broken fixed** — `Channel.<Channel>find()` Panache static bypasses CDI `@Alternative` InMemory stores, queries empty JPA table. Replaced with `tools.listChannels()`. Commit `c1f0032`.
- **CLAUDE.md** — two new testing conventions added: scheduler isolation via `@TestTransaction`; Panache statics bypass CDI alternatives.
- **Channel architecture discussion** — qhorus #131 enriched with design decisions: Qhorus is participant-blind (gateway in Qhorus, ClaudonyChannelBackend in Claudony, Slack/WhatsApp in connectors). Issues logged: claudony#98, connectors#2.

## Current State

*Unchanged — `git show HEAD~1:HANDOFF.md`*

## Immediate Next Step

*Unchanged — `git show HEAD~1:HANDOFF.md`*

## Key Architecture Facts

*Unchanged — `git show HEAD~1:HANDOFF.md`*

## References

| What | Path |
|---|---|
| Blog entry | `blog/2026-05-01-mdp01-two-bugs-that-looked-wrong.md` |
| Channel design decisions | `casehubio/qhorus#131` (comment 2026-05-01) |
| ClaudonyChannelBackend issue | `casehubio/claudony#98` |
| Previous handover (full context) | `git show HEAD~1:HANDOFF.md` |
