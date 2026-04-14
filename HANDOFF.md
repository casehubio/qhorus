# Quarkus Qhorus — Session Handover
**Date:** 2026-04-14 (third session)

## What Was Done This Session

**Phase 6 — Addressing** (issues #27–#31)
- `target` field on `send_message` (Flyway V2, schema, validation, DTO)
- `reader_instance_id` filter on all read tools — `instance:*`, `capability:*`, `role:*` dispatch
- EVENT bypass (telemetry always broadcast); BARRIER+role semantics documented and tested
- 41 tests

**Phase 9 — A2A compatibility** (issues #32–#35)
- `POST /a2a/message:send` → `send_message`; `GET /a2a/tasks/{id}` → task status
- Guarded by `quarkus.qhorus.a2a.enabled` (default false)
- Gotcha: RestAssured encodes `:` to `%3A` — use `urlEncodingEnabled(false)` for `message:send` tests
- 29 tests

**Phase 10 — Human-in-the-loop controls** (issues #36–#44)
- `pause_channel`, `resume_channel` (Flyway V3)
- `request_approval`, `respond_to_approval`, `list_pending_approvals`
- `cancel_wait`, `list_pending_waits` + wait_for_reply cancellation detection
- `force_release_channel` (BARRIER/COLLECT only)
- `revoke_artefact` (deletes SharedData + claims)
- `delete_message`, `clear_channel`, `deregister_instance`
- `channel_digest` — structured summary for human dashboards
- Watchdog alerting: `register_watchdog`, `list_watchdogs`, `delete_watchdog` + `WatchdogEvaluationService` + `WatchdogScheduler` (Flyway V4; guarded by `quarkus.qhorus.watchdog.enabled`)
- 103 tests

## Current State

- **Tests:** 439 passing, 0 failing
- **Last commit:** `c0b880b` docs: update DESIGN.md (phases 10 done)
- **Uncommitted:** CLAUDE.md (testing conventions added), blog/ (first entry), HANDOFF.md
- **Open issues:** none

## Critical Testing Conventions (new this session)

- **`@TestTransaction` + RestAssured HTTP don't share a transaction** — injected writes are uncommitted when HTTP handler fires. No `@TestTransaction` on tests that mix injected tool calls with RestAssured.
- **Raw `ExecutorService` loses Quarkus CDI context** — `@Transactional` silently broken on raw threads. Use `@Inject ManagedExecutor` instead for concurrent tests.
- **Optional modules** (`a2a`, `watchdog`) need `@TestProfile` that sets `quarkus.qhorus.<module>.enabled=true`.
- **Cancellation is concurrent, not pre-emptive** — `cancel_wait` must fire WHILE `wait_for_reply` is in its poll loop, not before. Use `ManagedExecutor` to start the wait first.

## Immediate Next Step

**Phase 11 — Access control and governance**

Run `issue-workflow` Phase 1 first. Key scope from the design spec:
- Per-channel write permissions (allowed `instance_id`s or `capability:tag`s)
- Admin role (designated instance can pause/resume/close channels on behalf of others)
- Rate limiting per channel or per instance
- Read-only observer mode (subscribe to events without appearing in instance registry)

## References

| What | Path |
|---|---|
| Design spec | `docs/specs/2026-04-13-qhorus-design.md` |
| Implementation tracker | `docs/DESIGN.md` (phases 1–7, 9–10 ✅; 8 is Claudony work; 11–12 pending) |
| Blog entry this session | `blog/2026-04-14-mdp01-addressing-a2a-hitl.md` |
| Garden PRs this session | Hortora/garden#46 (8 Quarkus entries) |
