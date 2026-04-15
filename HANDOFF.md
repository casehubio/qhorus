# Quarkus Qhorus — Session Handover
**Date:** 2026-04-15 (fifth session)

## What Was Done This Session

**New repo: `quarkus-ledger`** (`~/claude/quarkus-ledger`, https://github.com/mdproctor/quarkus-ledger)
- Extracted from quarkus-tarkus-ledger and generalised: `LedgerEntry` abstract base (JPA JOINED,
  `subjectId` aggregate key), `LedgerAttestation`, `ActorTrustScore`, `LedgerHashChain`,
  `TrustScoreComputer`, `TrustScoreJob`, SPI interfaces, `LedgerConfig` (prefix `quarkus.ledger`)
- Flyway V1000/V1001 base schema; jandex plugin; `JpaLedgerEntryRepository` marked `@Alternative`
- `LedgerHashChain.verify()` accepts `List<? extends LedgerEntry>`
- 33 unit tests; README + integration guide + examples.md
- `examples/order-processing/` — runnable Quarkus app, 8 IT tests (`mvn test`)
- `@ConfigRoot` fix: `quarkus.ledger.*` keys now recognised in consuming extensions
- Flyway ordering warning added to integration guide (subclass migration must be V1002+)
- Garden entry GE-20260415-d7a439 submitted (Hortora/garden#57) — Flyway ordering gotcha

**quarkus-tarkus** — migrated to `quarkus-ledger`:
- `WorkItemLedgerEntry extends LedgerEntry`, `WorkItemLedgerEntryRepository`
- 14 classes deleted; 69 tests passing; config prefix `quarkus.tarkus.ledger.*` → `quarkus.ledger.*`

**Phase 12 — Structured observability** (epic #50, issues #51–#54, all closed):
- `AgentMessageLedgerEntry` (V1002), `LedgerWriteService`, `list_events`, `get_channel_timeline`
- 36 new tests; 557 total passing
- DESIGN.md updated: Phase 12 ✅

## Current State

- **Tests:** 557 passing (quarkus-qhorus), 69 passing (quarkus-tarkus-ledger), 33 (quarkus-ledger)
- **Open issues:** none
- **Flyway:** V8 (Qhorus), V1002 (quarkus-ledger examples), V1000/V1001 (quarkus-ledger base)
- **Design phases:** 1–7 ✅, 9–12 ✅; Phase 8 (Claudony) pending

## Waiting On

Another Claude session is actively writing code in `~/claude/claudony`. **Do not start
Phase 8 integration until that session finishes.** Check `git log` in claudony before touching it.

## Immediate Next Step

Once the Claudony session completes: begin **Phase 8 — Embed in Claudony** (unified MCP endpoint).
Run issue-workflow Phase 1 in `quarkus-qhorus` before writing any code.

## References

| What | Path |
|---|---|
| Design spec | `docs/specs/2026-04-13-qhorus-design.md` |
| Implementation tracker | `docs/DESIGN.md` (phases 1–7, 9–12 ✅; 8 pending) |
| quarkus-ledger | `~/claude/quarkus-ledger/` |
| quarkus-ledger integration guide | `~/claude/quarkus-ledger/docs/integration-guide.md` |
| quarkus-ledger example | `~/claude/quarkus-ledger/examples/order-processing/` |
| Previous handover | `git show HEAD~1:HANDOFF.md` |
