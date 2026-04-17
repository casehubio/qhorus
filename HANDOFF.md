# Quarkus Qhorus — Session Handover
**Date:** 2026-04-17 (seventh session)

## What Was Done This Session

**quarkus-ledger supplement reconciliation (Qhorus side):**
- `LedgerWriteService`: `entry.correlationId` removed from `LedgerEntry` base — now set via
  `ObservabilitySupplement.attach()` when `message.correlationId` is non-null
- `QhorusMcpTools.toEventMap()`: reads `correlationId` via
  `entry.observability().map(obs -> obs.correlationId).orElse(null)`
- Flyway V1002 → V1003 for `agent_message_ledger_entry` (V1002 now reserved by
  quarkus-ledger supplement migration)
- Tests updated: `AgentLedgerCaptureTest` verifies correlationId via
  `entry.observability().get().correlationId`; `AgentMessageLedgerEntryTest` drops
  removed field assertion

**Schema management:**
- quarkus-ledger deleted all SQL migration scripts (deferred decision)
- Qhorus tests switched from Flyway → `hibernate-orm.database.generation=drop-and-create`
- Flyway config removed from `runtime/src/test/resources/application.properties`

**Phase 8 — Qhorus embedded in Claudony:**
- Added `quarkus-qhorus` + `quarkus-jdbc-h2` to Claudony pom (our commit `a99d2a8`)
- **Claudony is currently broken** — Claudony Claude subsequently removed the datasource
  config. Full fix briefing sent in-session (restores H2 dep + datasource properties +
  `hibernate-orm.database.generation`). Also at `docs/phase8-claudony-integration.md`.

**Error handling — completed:**
- `@WrapBusinessError({IAE, ISE})` on `QhorusMcpTools` → all 37 structured-return `@Tool`
  methods produce `isError:true` instead of JSON-RPC errors
- ADR-0001 and DESIGN.md §MCP Tool Design updated to reflect resolution

## Current State

- **Tests:** 561 passing, 0 failing
- **Open issues:** none
- **Claudony:** broken — waiting on Claudony Claude to apply datasource fix

## Immediate Next Step

Once Claudony Claude confirms 231 tests green: make `LedgerWriteService` defensive —
a failed ledger write should not roll back `send_message` (currently it does, because
both run in the same `@Transactional` boundary).

## References

| What | Path |
|---|---|
| Design spec | `docs/specs/2026-04-13-qhorus-design.md` |
| Implementation tracker | `docs/DESIGN.md` |
| MCP tool strategy ADR | `adr/0001-mcp-tool-return-type-strategy.md` |
| Phase 8 Claudony briefing | `docs/phase8-claudony-integration.md` |
| Previous handover | `git show HEAD~1:HANDOFF.md` |
| Garden batch PR | Hortora/garden#69 (5 entries: jar xf, @WrapBusinessError×3, GE-0133 revision) |
