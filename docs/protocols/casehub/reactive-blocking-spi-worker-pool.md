---
id: PP-20260613-885e08
title: "Reactive services calling blocking SPI methods must shift execution to Infrastructure.getDefaultWorkerPool()"
type: rule
scope: repo
applies_to: "runtime/ — any reactive service bean (@IfBuildProperty reactive.enabled) that invokes a blocking SPI method"
severity: important
refs:
  - runtime/src/main/java/io/casehub/qhorus/runtime/message/ReactiveMessageService.java
  - docs/protocols/casehub/qhorus-reactive-gating.md
violation_hint: "Calling a blocking SPI (e.g. ObligorTrustPolicy.permits(), StoredMessageTypePolicy.validate()) directly from a reactive chain without runSubscriptionOn — silently blocks the Vert.x I/O thread under load"
garden_ref: "GE-20260529-ff186e"
created: 2026-06-13
---

Reactive service beans run on the Vert.x event-loop thread. Blocking SPI methods (JPA
queries, synchronous policy checks) must not execute on this thread — they stall the
event loop and degrade concurrency for all channels. Use
`Uni.createFrom().item(() -> spi.method(ctx)).runSubscriptionOn(Infrastructure.getDefaultWorkerPool())`
to shift the blocking call to a Quarkus worker thread. The fast-path short-circuit
(e.g. threshold ≤ 0 → permit immediately) should execute inline before creating the `Uni`
to avoid unnecessary thread hops. This pattern was established for `ObligorTrustPolicy`
in `ReactiveMessageService` (#235) and applies to any blocking SPI injected into a
reactive service.
