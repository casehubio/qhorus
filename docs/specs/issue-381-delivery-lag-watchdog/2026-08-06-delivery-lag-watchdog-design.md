# DELIVERY_LAG Watchdog Condition — Bug Fixes

**Issue:** casehubio/qhorus#381
**Date:** 2026-08-06
**Status:** Approved (revised — feature already implemented, fixing bugs found by design review)

## Situation

DELIVERY_LAG is already implemented on main. A light design review surfaced
four bugs in the existing implementation. This spec captures the fixes.

## Bugs

### 1. Lag arithmetic uses sequence ID distance, not message count (HIGH)

`evaluateDeliveryLag()` computes `lag = latestId - delivered` where both
values are global PostgreSQL sequence IDs (`allocationSize=50`, shared
across all channels and tenancies). The difference is not a message count.

A channel with 3 undelivered messages could show lag=5200 if other channels
consumed sequence values in between. With threshold=50 (current default),
this fires incorrectly.

**Fix:** Count actual undelivered messages per member using
`crossTenantMessageStore.count(MessageQuery.builder().channelId(chId).afterId(cursorId).build())`.
This gives the real message count, not a sequence gap.

### 2. Null cursor produces false positives (HIGH)

New members have null `lastDeliveredMessageId`. The code treats null as
`0L`, producing `lag = latestId` — tens of thousands on active systems.
Every new member triggers DELIVERY_LAG immediately.

**Fix:** Skip members with null `lastDeliveredMessageId`. Their lag is
undefined (no delivery confirmed yet), not infinite.

### 3. Debounce effectively disabled (HIGH)

DELIVERY_LAG uses `thresholdCount` only. `thresholdSeconds` is null, so
`isDebounced()` falls through to a 1-second window. The condition fires
every evaluation cycle (~30s) while any member is lagging.

**Fix:** Use `thresholdSeconds` as the debounce window with a default of
300s. The dual-purpose pattern matches LOOP_DETECTED (which uses
`thresholdCount` for repetitions and `thresholdSeconds` for time window).

### 4. Self-referential loop with wildcard (MEDIUM)

`targetName="*"` evaluates the notification channel. Each alert creates a
new message, advancing the head, creating more lag on the notification
channel — a feedback loop.

**Fix:** Exclude channels matching `w.notificationChannel()` from evaluation.

## Files Changed

| File | Change |
|------|--------|
| `runtime/.../watchdog/WatchdogEvaluationService.java` | Fix all 4 bugs in `evaluateDeliveryLag()` |
| `runtime/.../watchdog/WatchdogEvaluationServiceTest.java` | Add regression tests for each bug |
| `connectors/.../ConnectorAlertBridgeTest.java` | Update if `DeliveryLagContext` shape changes |
| `api/.../watchdog/DeliveryLagContext.java` | Update `LagDetail` — remove `lastDeliveredId` (sequence ID), replace with `undeliveredCount` (actual count) |
