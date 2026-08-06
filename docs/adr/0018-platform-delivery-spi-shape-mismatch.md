# ADR-0018: Platform Delivery SPI Shape Mismatch

**Status:** Accepted
**Date:** 2026-08-06
**Refs:** casehubio/qhorus#378, casehubio/qhorus#376

## Context

The platform defines a delivery SPI (`DeliveryAttempt`, `EngagementEvent`)
designed for notification delivery: multi-attempt, multi-channel, per-attempt
failure reasons, retry with backoff, engagement tracking (opened, clicked).

Qhorus message delivery is cursor-shaped: a high-water mark
(`lastDeliveredMessageId`) per participant per channel, advanced forward on
each successful delivery. There is no per-message retry, no multi-channel
fan-out per recipient, and no engagement tracking.

## Decision

Do not integrate qhorus delivery with the platform delivery SPI.

The shape mismatch makes integration counterproductive:

- **Cursor vs. attempt:** Qhorus advances a single monotonic cursor.
  The platform SPI expects a `DeliveryAttempt` per message per channel per
  recipient, with `attemptCount`, `nextRetryAt`, and `failureReason`. Mapping
  a cursor advance to an attempt record adds bookkeeping with no informational
  gain.

- **No engagement model:** Qhorus delivers messages to backends (agents,
  webhooks, WebSockets). There is no "opened" or "clicked" concept — delivery
  confirmation is binary (cursor advanced or not).

- **Retry granularity:** Qhorus retries at the batch level via
  `DeliveryService` reconciliation, not per-message. The platform SPI's
  per-attempt retry scheduling (`nextRetryAt`) has no counterpart.

## Consequences

- Qhorus delivery metrics remain internal (Micrometer counters, delivery
  cursor queries, `DELIVERY_LAG` watchdog condition).
- The platform notification engine and qhorus delivery operate independently.
- Re-evaluate if the platform SPI gains a cursor-shaped delivery model or
  if qhorus adds per-message delivery tracking beyond the current cursor.
