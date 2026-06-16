# ADR-0016: Hybrid Channel Type Enforcement — COMMAND/QUERY Hard, Others Advisory

**Date:** 2026-06-16
**Status:** Accepted
**Issue:** casehubio/qhorus#271

## Context

Channel `allowedTypes` and `deniedTypes` constraints were hard-enforced for all message types:
`StoredMessageTypePolicy.validate()` threw `MessageTypeViolationException` on any violation.

When an LLM agent sends COMMAND to the wrong channel under full advisory enforcement and receives
an advisory signal, it corrects itself by dispatching to the right channel. This creates either
a duplicate OPEN Commitment (same correlationId, two channels) or an orphaned OPEN Commitment —
stalled permanently with no mechanism to distinguish it from a genuine governance failure.

## Decision

Enforce COMMAND and QUERY violations hard; treat all other type violations as advisory.

**Rationale:**
- COMMAND and QUERY are the only types that call `commitmentService.open()`. Advisory dispatch
  on the wrong channel followed by LLM correction creates orphan Commitments.
- All other types (STATUS, EVENT, DONE, FAILURE, DECLINE, RESPONSE, HANDOFF) do not create
  Commitments. Advisory dispatch for these types produces an accurate audit record (WARN log +
  ledger entry + `DispatchResult.advisories()`) without orphan risk.
- Hard enforcement for non-obligation-creating types erases constraint violations from the ledger.
  Advisory enforcement makes them visible and attributable.
- HANDOFF is safe: `CommitmentService.delegate()` creates the child Commitment with the parent's
  `channelId` (not the HANDOFF message's channel), so a HANDOFF on the wrong channel does not
  orphan the child Commitment.

## Implementation

- `MessageTypePolicy.advisory(Channel, MessageType)` — new default method; returns null by default
- `StoredMessageTypePolicy.validate()` — hard-enforces COMMAND/QUERY only (no-op for others)
- `StoredMessageTypePolicy.advisory()` — returns violation text for non-COMMAND/QUERY types
- `DispatchResult.advisories` — new `@JsonInclude(NON_EMPTY) List<String>` field
- `MessageService.dispatch()` — calls validate() then advisory(); logs WARN; threads advisories
- `ReactiveMessageService.dispatch()` — same via AtomicReference across lambda boundaries
- `QhorusMcpTools`/`ReactiveQhorusMcpTools` — removed redundant client-side validate() call

## Consequences

**Positive:**
- Normative record is more complete — constraint violations for non-obligation-creating types are
  visible in the ledger and DispatchResult rather than silently blocked.
- LLM agents receive advisory signal without a hard error, enabling graceful correction.

**Known limitation:**
- `wait_for_reply` polls by (channelId, correlationId). If an LLM sends DONE/RESPONSE to the
  wrong channel, the Commitment is correctly fulfilled but `wait_for_reply` on the correct channel
  times out. Self-correcting: the DONE-sender sees the advisory and can retry on the correct
  channel; `CommitmentService.fulfill()` is idempotent (already-FULFILLED commitment is a silent
  no-op) so the retry succeeds without corruption.
