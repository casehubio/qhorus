# DELIVERY_LAG Watchdog Condition — Design Spec

**Issue:** casehubio/qhorus#381
**Date:** 2026-08-06
**Status:** Approved

## Problem

Existing watchdog conditions (BARRIER_STUCK, CONVERSATION_STALL) fire after
coordination has already stalled. There is no early warning for delivery
infrastructure failures — a crashed webhook endpoint, a disconnected SSE
stream, or a dead agent — that would allow intervention before coordination
breaks down.

## Solution

Add `DELIVERY_LAG` as a new watchdog condition type. It fires when any
channel member's `lastDeliveredMessageId` cursor falls behind the channel's
latest message ID by a configurable message-count threshold.

## Design Decisions

### Threshold: count-only

Uses `Watchdog.thresholdCount()` (default 50). "Fire when any participant
is >= N messages behind the channel head."

Time-based lag was rejected: it conflates channel inactivity (no new
messages) with delivery failure (messages exist but aren't delivered).
Time-based coordination problems are already covered by CONVERSATION_STALL
and BARRIER_STUCK.

### Alert shape: per-channel aggregate

One alert per channel, carrying the full list of lagging members with
individual lag counts. Matches the BARRIER_STUCK pattern (one alert with
`missingContributors`). Avoids alert storms when multiple participants lag
on the same channel simultaneously.

### Tracking gate: skip silently

Channels without delivery tracking enabled (`isDeliveryTrackingEnabled(ch)`
returns false) are skipped silently. No advisory alert for misconfiguration.
Consistent with how BARRIER_STUCK gates its delivery enrichment.

### No cross-condition suppression

DELIVERY_LAG fires independently of BARRIER_STUCK and CONVERSATION_STALL.
Both firing simultaneously is diagnostic signal: "the barrier is stuck
*because* delivery is lagging, not because the agent is ignoring the
message." No other conditions suppress each other today.

## Components

### 1. WatchdogConditionType.DELIVERY_LAG

New enum value in `api/src/main/java/.../watchdog/WatchdogConditionType.java`.

### 2. DeliveryLagContext

New sealed record in `api/src/main/java/.../watchdog/`:

```java
public record DeliveryLagContext(
        UUID channelId,
        String channelName,
        List<LagDetail> laggingMembers,
        long latestMessageId
) implements AlertContext {

    public record LagDetail(String memberId, long lastDeliveredId, long lag) {}

    @Override
    public WatchdogConditionType conditionType() {
        return WatchdogConditionType.DELIVERY_LAG;
    }
}
```

Fields:
- `channelId` / `channelName` — which channel has lagging participants
- `laggingMembers` — list of members exceeding the threshold, each with
  their current cursor position and computed lag
- `latestMessageId` — the channel head at evaluation time (reference point)

`LagDetail.lastDeliveredId` is 0 when `lastDeliveredMessageId` is null
(member has never received any delivery).

### 3. WatchdogEvaluationService.evaluateDeliveryLag()

Evaluation logic:

1. Filter channels by `targetName` match (or `*`) and
   `ChannelService.isDeliveryTrackingEnabled(ch)`. Skip if tracking disabled.
2. Get latest message ID via `crossTenantMessageStore.findLastMessage(ch.id())`.
   Skip if channel is empty.
3. Get all members via `channelMembershipStore.findByChannel(ch.id())`.
4. For each member: compute `lag = latestId - lastDeliveredId` (null cursor
   treated as 0 — lag = latestId). Collect members where `lag >= threshold`.
5. If any lagging members: fire one alert per channel.

Default threshold: 50 messages (via `thresholdCount`, consistent with
QUEUE_DEPTH pattern).

### 4. ConnectorAlertBridge.buildBody()

New switch case for `DeliveryLagContext`:

```
DELIVERY_LAG: N participant(s) lagging on 'channel-name'
Channel: channel-name
Lagging members: member-a (lag: 120), member-b (lag: 85)
Channel head: 1542
```

### 5. register_watchdog MCP tool

Add `DELIVERY_LAG` to the condition_type description and enum validation
string. No new parameters — uses existing `thresholdCount`.

### 6. CLAUDE.md

Document the new condition type in the watchdog section.

## Testing Strategy

CDI-free unit tests in `WatchdogEvaluationServiceTest`:

1. **Happy path** — channel with tracking enabled, two members, one behind
   threshold, one ahead. Assert: fires with correct lagging member.
2. **All caught up** — all members within threshold. Assert: does not fire.
3. **Tracking disabled** — channel without tracking. Assert: skipped silently.
4. **Null cursor** — member with null `lastDeliveredMessageId`. Assert:
   treated as lag = latestId (never delivered).
5. **Empty channel** — no messages. Assert: does not fire.
6. **Wildcard target** — `*` matches all channels. Assert: evaluates all
   channels with tracking enabled.
7. **Debounce** — `lastFiredAt` within threshold window. Assert: skipped.

ConnectorAlertBridge test:
8. **Body formatting** — verify `buildBody()` output for DeliveryLagContext.

## Not In Scope

- Time-based lag threshold (rejected — see Design Decisions)
- Cross-condition suppression with BARRIER_STUCK
- Reactive parity (tracked separately, all reactive watchdog work deferred)
- Per-participant retry (#380 — separate issue)
