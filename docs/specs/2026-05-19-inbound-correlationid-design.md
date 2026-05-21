# Design Spec — Inbound correlationId Threading (#154)

**Epic:** epic-154-inbound-correlationid  
**Issue:** casehubio/qhorus#154  
**Date:** 2026-05-19

---

## Problem

`ChannelGateway.receiveHumanMessage()` hardcodes `correlationId = null` when calling
`messageService.send()`. Human replies arriving via `HumanParticipatingChannelBackend`
never carry a correlationId — so `CommitmentService` cannot auto-fulfill on RESPONSE,
DONE, or DECLINE. Commitments opened by COMMAND messages stay OPEN forever when the
human responds via the gateway.

Clinical works around this with a manual `commitmentService.fulfill(deviationId.toString())`
call in `PiResponseListener`, explicitly noting the workaround in a comment referencing
this issue. That workaround is tracked for removal in casehubio/clinical#16.

The root cause is that `NormalisedMessage` carries only 3 of the 9 `messageService.send()`
parameters — `type`, `content`, `senderInstanceId` — forcing the gateway to hardcode the
rest as null. The normaliser SPI is an incomplete translation point.

---

## Design Decision

**Approach B — complete `NormalisedMessage`; add only `correlationId` to `InboundHumanMessage`.**

`NormalisedMessage` expands to all 7 domain-format params needed by `messageService.send()`.
This makes the normaliser SPI the single complete translation point; the gateway never
hard-codes parameters again.

`InboundHumanMessage` gains only `correlationId` — the one field with a concrete use case
and a known type that any backend can reasonably supply. Other fields (`artefactRefs`,
`inReplyTo`, `target`) are deliberately deferred to targeted changes when a backend needs
them (tracked in casehubio/qhorus#159). Adding them now would lock in type choices before
any use case exists; `metadata` covers backend-specific extras in the interim.

---

## Changes

### `casehub-qhorus-api` module

**`InboundHumanMessage`** — gains one nullable field:

```java
public record InboundHumanMessage(
    String externalSenderId,
    String content,
    Instant receivedAt,
    Map<String, String> metadata,
    String correlationId) {}      // nullable — null when human is initiating
```

**`NormalisedMessage`** — expands from 3 fields to 7 (complete domain representation):

```java
public record NormalisedMessage(
    MessageType type,
    String content,
    String senderInstanceId,
    String correlationId,     // nullable
    Long inReplyTo,           // nullable
    String artefactRefs,      // nullable
    String target) {}         // nullable
```

`InboundNormaliser` SPI interface is **unchanged** — signature stays
`NormalisedMessage normalise(ChannelRef, InboundHumanMessage)`.

### `casehub-qhorus` runtime module

**`DefaultInboundNormaliser`** — passes `correlationId` through; nulls the new fields:

```java
return new NormalisedMessage(
    MessageType.QUERY,
    raw.content(),
    "human:" + raw.externalSenderId(),
    raw.correlationId(), null, null, null);
```

Note: `DefaultInboundNormaliser` continues to hardcode `QUERY` regardless of correlationId
presence. Type inference from correlationId context is a separate design concern tracked
in casehubio/qhorus#158.

**`ChannelGateway.receiveHumanMessage()`** — becomes a clean 1:1 mapping, no hard-coded nulls:

```java
public void receiveHumanMessage(ChannelRef channel, InboundHumanMessage raw) {
    NormalisedMessage n = normaliser.normalise(channel, raw);
    messageService.send(channel.id(), n.senderInstanceId(),
        n.type(), n.content(),
        n.correlationId(), n.inReplyTo(),
        n.artefactRefs(), n.target(), ActorType.HUMAN);
}
```

### `casehub-clinical` module

**`ClinicalInboundNormaliser`** — passes `correlationId` through alongside existing
type-detection logic:

```java
return new NormalisedMessage(
    type,
    raw.content(),
    "human:" + raw.externalSenderId(),
    raw.correlationId(), null, null, null);
```

### Call site updates (compile fixes, not behaviour changes)

Every existing constructor call for the two changed records gets trailing nulls:

| Record | Locations |
|--------|-----------|
| `InboundHumanMessage` | `DefaultInboundNormaliserTest` (×4), `ChannelGatewayE2ETest` (×1), `ChannelGatewayTest` (×N), `ChannelGatewayRobustnessTest` (×N), `ClinicalInboundNormaliserTest` (×N) |
| `NormalisedMessage` | `ClinicalInboundNormaliser` (×1), any direct test instantiations |

---

## Testing

### Unit — `DefaultInboundNormaliserTest` (additions)

- `normalise_withCorrelationId_passesThrough()` — raw carries `"corr-99"`, assert `normalised.correlationId().equals("corr-99")`
- `normalise_nullCorrelationId_propagatesNull()` — raw carries null, assert `normalised.correlationId()` is null
- `normalise_newNullableFields_areNull()` — assert `inReplyTo`, `artefactRefs`, `target` are all null

### Unit — `ClinicalInboundNormaliserTest` (additions)

- `normalise_oversightChannel_passesCorrelationId()` — correlationId threads through DONE/DECLINE branch
- `normalise_nonOversightChannel_passesCorrelationId()` — correlationId threads through QUERY branch

### Integration — `ChannelGatewayE2ETest` (additions)

**Key test:** `receiveHumanMessage_withCorrelationId_fulfillsCommitment()`

1. Send COMMAND via `tools.sendMessage()` with a correlationId → commitment opens
2. Call `gateway.receiveHumanMessage()` with `InboundHumanMessage(correlationId = same value)`
3. Clinical normaliser returns DONE; `MessageService.send()` triggers `commitmentService.fulfill()`
4. Assert commitment is FULFILLED

Note: this test requires a custom `@Alternative InboundNormaliser` that returns DONE
(not the `DefaultInboundNormaliser` which always returns QUERY). Since the DefaultInboundNormaliser
type-inference gap is out of scope (#158), the integration test wires a test-local normaliser.

**Regression test:** `receiveHumanMessage_withoutCorrelationId_leavesCommitmentOpen()`

1. Send COMMAND with correlationId
2. Call `gateway.receiveHumanMessage()` with null correlationId
3. Assert commitment remains OPEN

---

## Deferred Concerns

| Concern | Issue |
|---------|-------|
| `DefaultInboundNormaliser` type inference — QUERY when correlationId non-null is semantically wrong | casehubio/qhorus#158 |
| `PiResponseListener` manual commitment workaround removal (after #153 + #154 both ship) | casehubio/clinical#16 |
| `InboundHumanMessage` expansion with `artefactRefs`, `inReplyTo`, `target` | casehubio/qhorus#159 |

---

## Out of Scope

- `ObserverSignal` — observer signals are always EVENT; no obligation semantics; no correlationId needed
- `ReactiveQhorusMcpTools` / `ReactiveMessageService` — reactive path follows blocking path; no separate changes needed
- Flyway migrations — no schema changes
- `LedgerSPI` propagation — no changes to `LedgerEntryRepository`
