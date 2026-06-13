---
id: PP-20260613-3a569e
title: "SseEventSink.send() is async — chain sink.close() via thenRun/whenComplete, never synchronously"
type: rule
scope: repo
applies_to: "runtime/api/ — any JAX-RS SSE endpoint using SseEventSink"
severity: important
refs:
  - runtime/src/main/java/io/casehub/qhorus/runtime/api/A2AResource.java
violation_hint: "Calling sink.close() synchronously immediately after sink.send() logs IllegalStateException: Response has already been written at ERROR level on every terminal SSE send"
garden_ref: "GE-20260613-c29bb8"
created: 2026-06-13
---

`SseEventSink.send()` in Quarkus RESTEasy Reactive returns a `CompletionStage<?>` whose
completion signals that the HTTP write has finished. Calling `sink.close()` synchronously
after `send()` races against the async write and causes `IllegalStateException: Response has
already been written` — logged at ERROR on every terminal event delivery. Chain `sink.close()`
inside `.thenRun(() -> { if (!sink.isClosed()) sink.close(); })` or inside a `.whenComplete()`
callback. The same applies to consumer deregistration: call `deregisterStream()` inside the
`whenComplete` callback, not after the `send()` call returns.
