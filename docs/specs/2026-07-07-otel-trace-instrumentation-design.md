# OTel Trace Instrumentation Design

**Issue:** #197
**Date:** 2026-07-07
**Status:** Approved

## Problem

Qhorus has zero OpenTelemetry integration. Agent decisions, COMMAND dispatches,
HANDOFF chains, and DONE/FAILURE outcomes are invisible in distributed traces.
The `MessageLedgerEntry.traceId` field exists (inherited from casehub-ledger's
`LedgerEntry`) and the enrichment pipeline (`TraceIdEnricher` →
`OtelTraceIdProvider`) is wired, but without an active OTel SDK there is no
span to read from — the pipeline runs dry.

## Approach

Explicit `Tracer` injection into the four key services. No interceptors, no
abstraction layer. When no OTel SDK is on the classpath, `Tracer` resolves to
no-op and all operations are silently discarded. Config gates provide
per-feature control for consumers who have the SDK but want to reduce span
volume.

## Dependencies

**Qhorus `runtime/pom.xml`:**
- `io.opentelemetry:opentelemetry-api` — `<optional>true</optional>`, no
  explicit `<version>`. The Quarkus BOM (imported by both the qhorus parent
  POM and `casehub-parent`) manages the `opentelemetry-api` version aligned
  with the Quarkus platform release. Consumer applications importing the same
  Quarkus BOM are guaranteed version alignment.

**Qhorus `runtime/pom.xml` test scope:**
- `io.opentelemetry:opentelemetry-sdk-testing`

**No `quarkus-opentelemetry` in Qhorus itself.** Consumers add it to activate
tracing. Without it, everything is no-op.

**No Flyway migrations.** `traceId` already exists on `LedgerEntry`. This work
creates the spans that the existing `TraceIdEnricher` reads from.

## Configuration

```
casehub.qhorus.tracing.enabled=true          # master switch
casehub.qhorus.tracing.dispatch=true          # MessageService.dispatch()
casehub.qhorus.tracing.commitments=true       # CommitmentService transitions
casehub.qhorus.tracing.fan-out=true           # ChannelGateway.fanOut()
casehub.qhorus.tracing.ledger-write=true      # LedgerWriteService.record()
casehub.qhorus.tracing.delivery=true          # ChannelGateway.deliverRemote() + DeliveryService
```

All default `true`. Runtime config (`@ConfigMapping`), not build-time.

```java
@ConfigMapping(prefix = "casehub.qhorus.tracing")
public interface QhorusTracingConfig {
    @WithDefault("true") boolean enabled();
    @WithDefault("true") boolean dispatch();
    @WithDefault("true") boolean commitments();
    @WithDefault("true") boolean fanOut();
    @WithDefault("true") boolean ledgerWrite();
    @WithDefault("true") boolean delivery();
}
```

Guard pattern at each instrumentation point:

```java
if (tracingConfig.enabled() && tracingConfig.dispatch()) {
    span = tracer.spanBuilder("qhorus.dispatch")...startSpan();
}
```

## Span Design

### Dispatch span — `qhorus.dispatch`

**Where:** `MessageService.dispatch()` and `ReactiveMessageService.dispatch()`

**Span kind:** `INTERNAL`

**Span boundary:** Span starts after the paused check passes. Span ends when
`dispatch()` returns (blocking path) or when the dispatch `Uni` completes
(reactive path). The span covers all synchronous work within the method
scope — channel resolution, enforcement, message insert, commitment
transitions, ledger write, fan-out, and observer dispatch scheduling.

In the blocking path, observer callbacks fire post-commit via a JTA
`afterCompletion(STATUS_COMMITTED)` synchronization — this is outside the
span's scope. The `qhorus.observer.dispatch` event records when the observer
dispatch is *scheduled* (i.e., when `MessageObserverDispatcher.dispatch()` is
called), not when observers actually execute.

In the reactive path, observer dispatch runs synchronously within the `Uni`
chain (after the Panache transaction completes), so it is within the span's
scope.

**Attributes (set after channel resolution and enforcement):**

| Attribute | Source |
|-----------|--------|
| `qhorus.channel.id` | resolved channel UUID |
| `qhorus.channel.name` | resolved channel name |
| `qhorus.channel.semantic` | channel semantic (e.g. `LAST_WRITE`, `APPEND`) |
| `qhorus.message.type` | MessageType enum |
| `qhorus.message.sender` | sender ID |
| `qhorus.message.correlation_id` | if present |
| `qhorus.message.target` | if present |
| `qhorus.actor.type` | AGENT, HUMAN, SYSTEM |
| `qhorus.tenancy.id` | tenant ID |

**Span events (annotations, not child spans):**

| Event | When |
|-------|------|
| `qhorus.enforcement.acl` | AllowedWritersPolicy check (attr: result) |
| `qhorus.enforcement.rate_limit` | RateLimiter check |
| `qhorus.enforcement.trust` | ObligorTrustPolicy (COMMAND + named target only) |
| `qhorus.enforcement.type_policy` | MessageTypePolicy validation |
| `qhorus.observer.dispatch` | MessageObserverDispatcher.dispatch() invoked (attr: observer count). In blocking path, records scheduling — observers fire post-commit outside span scope. |

#### LAST_WRITE overwrite path

When the channel semantic is `LAST_WRITE` and an existing message from the
same sender is found, the dispatch follows a distinct early-return path that
overwrites the existing message. This path:

- Updates the existing message in place
- Calls `channelGateway.fanOut()`
- Registers delivery signal and broadcast (post-commit in blocking, inline in reactive)
- **Skips** commitment transitions, ledger write, and observer dispatch

The dispatch span still covers this path. The `qhorus.channel.semantic`
attribute distinguishes it. Only enforcement events and the fan-out child
span apply — commitment, ledger, and observer events are absent.

### Commitment transition spans — `qhorus.commitment.<action>`

**Where:** `CommitmentService` and `ReactiveCommitmentService`

**Span kind:** `INTERNAL`

**Span names:** `qhorus.commitment.open`, `qhorus.commitment.acknowledge`,
`qhorus.commitment.fulfill`, `qhorus.commitment.decline`,
`qhorus.commitment.fail`, `qhorus.commitment.delegate`,
`qhorus.commitment.expire_overdue`, `qhorus.commitment.extend_deadline`

**Attributes:**

| Attribute | Source |
|-----------|--------|
| `qhorus.commitment.id` | commitment UUID |
| `qhorus.commitment.correlation_id` | correlation ID |
| `qhorus.commitment.from_state` | previous state (null for open) |
| `qhorus.commitment.to_state` | new state |
| `qhorus.commitment.obligor` | obligated actor |
| `qhorus.channel.id` | channel UUID |

**Parent-child relationships:**

- `open`, `acknowledge`, `fulfill`, `decline`, `fail`, `delegate`: child spans
  of the enclosing `dispatch()` span — parent-child relationship happens
  automatically via OTel context propagation on the same thread (blocking)
  or Vert.x duplicated context (reactive).
- `expire_overdue`: **root span** (no parent). Called by a scheduler, not from
  within dispatch(). Attributes include `qhorus.commitment.expired_count`
  (number of commitments transitioned). Individual commitments are recorded
  as span events with `commitment_id`, `correlation_id`, and `obligor`.
- `extend_deadline`: **root span** when called outside dispatch (e.g., via
  API). Attributes include `qhorus.commitment.new_deadline`.

### Fan-out span — `qhorus.fanout`

**Where:** `ChannelGateway.fanOut()`

**Span kind:** `INTERNAL`

**Attributes:**

| Attribute | Source |
|-----------|--------|
| `qhorus.channel.id` | channel UUID |
| `qhorus.fanout.backend_count` | number of backends |
| `qhorus.fanout.has_tracked` | AT_LEAST_ONCE backends present |

**Per-backend child spans:** Each backend dispatch in a virtual thread creates
a child span `qhorus.fanout.backend` using `Context.current().wrap(runnable)`
to propagate the OTel context into the virtual thread. The parent `qhorus.fanout`
span ends when `fanOut()` returns; child spans complete independently when
`backend.post()` finishes. OTel supports children outliving their parent.

Per-backend child span attributes:

| Attribute | Source |
|-----------|--------|
| `qhorus.fanout.backend_id` | backend identifier |
| `qhorus.fanout.delivery_guarantee` | BEST_EFFORT or AT_LEAST_ONCE |

The child span records success or failure as span status. On exception,
the error is recorded on the child span.

AT_LEAST_ONCE backends are skipped by `fanOut()` (the delivery pump handles
them) — no child span is created for skipped backends. The
`qhorus.fanout.has_tracked` attribute on the parent span signals their
existence.

Child span of `dispatch()`.

### Delivery spans — `qhorus.delivery.remote` and `qhorus.delivery.pump`

**Where:** `ChannelGateway.deliverRemote()` and `DeliveryService.deliverPending()`

These two methods handle delivery paths that are independent of dispatch():

- `deliverRemote()` — cross-node BEST_EFFORT delivery triggered by a
  broadcast notification. Delivers to local backends after receiving a
  remote event.
- `DeliveryService.deliverPending()` — AT_LEAST_ONCE delivery pump. Retries
  delivery to tracked backends using cursor-based pagination.

**Span kind:** `INTERNAL`

**`qhorus.delivery.remote` attributes:**

| Attribute | Source |
|-----------|--------|
| `qhorus.channel.id` | channel UUID |
| `qhorus.delivery.message_id` | message ID being delivered |
| `qhorus.delivery.backend_count` | number of backends receiving delivery |

Per-backend dispatch uses the same virtual-thread child span pattern as
`fanOut()` — `Context.current().wrap()` propagates context.

**`qhorus.delivery.pump` attributes:**

| Attribute | Source |
|-----------|--------|
| `qhorus.channel.id` | channel UUID |
| `qhorus.delivery.backend_id` | target backend |
| `qhorus.delivery.cursor_position` | current cursor |
| `qhorus.delivery.batch_size` | messages delivered in this batch |

Both are root spans — they execute outside any dispatch context.

### Ledger write span — `qhorus.ledger.write`

**Where:** `LedgerWriteService.record()` and `ReactiveLedgerWriteService.record()`

**Span kind:** `INTERNAL`

**Attributes:**

| Attribute | Source |
|-----------|--------|
| `qhorus.ledger.entry_type` | message type |
| `qhorus.ledger.channel_id` | channel UUID |
| `qhorus.ledger.message_id` | message ID |
| `qhorus.ledger.has_attestation` | whether attestation was written |

**Transactional semantics differ by stack:**

- **Blocking:** `LedgerWriteService.record()` runs in `REQUIRES_NEW` — a
  separate transaction from the enclosing dispatch. Ledger entries survive
  outer transaction rollback.
- **Reactive:** `ReactiveLedgerWriteService.record()` joins the enclosing
  `Panache.withTransaction()` — no `REQUIRES_NEW` equivalent exists in
  Hibernate Reactive. The ledger entry is atomic with the message insert.

In both cases, the span is a child of `dispatch()` — the parent-child
relationship is unaffected by transactional boundaries.

### Cross-request span links

For obligation lifecycle (COMMAND → DONE/FAILURE/DECLINE): the terminal
message's dispatch span gets a **span link** to the original COMMAND's trace.

**Query mechanism:** When `LedgerWriteService.record()` processes a terminal
message with a `correlationId`, it uses the existing
`messageRepo.findEarliestWithSubjectByCorrelationId(correlationId, tenancyId)`
to retrieve the original COMMAND's `MessageLedgerEntry`. The earliest entry
for a correlation thread is the COMMAND/QUERY that created it, so no new
repository method is needed.

The `traceId` field on the returned entry (populated by `TraceIdEnricher`
during the COMMAND's original `ledger.save()`) is used to construct a
`SpanContext` for the link.

**Null traceId handling:** If the original entry's `traceId` is null — because
the consumer was not running OTel when the COMMAND was dispatched, or because
the OTel SDK was added after the COMMAND — the span link is silently skipped.
No failure, no log noise. The terminal message's own span is still created
normally; only the cross-request correlation is absent.

Same approach for HANDOFF chains — the delegation commitment's span links back
to the parent commitment's trace.

### Reactive Span Lifecycle

The blocking span pattern (`try (Scope scope = span.makeCurrent()) { ... }
finally { span.end(); }`) does not translate directly to reactive chains.

**Pattern for Mutiny chains:**

```java
Span span = tracer.spanBuilder("qhorus.dispatch").startSpan();
Scope scope = span.makeCurrent();
return doDispatch(dispatch)
    .onFailure().invoke(t -> {
        span.setStatus(StatusCode.ERROR);
        span.recordException(t);
    })
    .onTermination().invoke(() -> {
        scope.close();
        span.end();
    });
```

**Operator ordering is critical.** In Mutiny, operators closer to the
upstream (source) fire first during signal propagation. `onFailure` must
be chained before `onTermination` so that on failure:

1. `onFailure` fires first — records error status and exception while the
   span is still active.
2. `onTermination` fires second — closes the scope and ends the span.

Reversing this order (onTermination before onFailure) would end the span
before the error is recorded. Per the OTel API specification, mutations
to an ended span are no-ops — the error would be silently lost.

On success, `onFailure` does not fire; `onTermination` correctly ends
the span with OK status. On cancellation, only `onTermination` fires.

**Why context propagation works:** When `quarkus-opentelemetry` is on the
consumer's classpath, Quarkus replaces the default OTel `ContextStorage`
with Vert.x's `ContextStorage`. Span context is stored in the Vert.x
duplicated context — not in thread-locals. Mutiny operators that execute
on the same Vert.x context (which is the default for `withTransaction`,
`flatMap`, `map`, etc.) share this context automatically.

This means:
1. `Span.current()` works correctly across Mutiny operator boundaries —
   child spans created anywhere in the chain see the parent.
2. `scope.close()` in `onTermination` correctly closes the scope in the
   Vert.x context, regardless of which thread executes the terminal operator.
3. The `Scope` created by `makeCurrent()` writes to the Vert.x context,
   not a thread-local, so it is visible across async operator hops.

**Child spans (commitment, fan-out, ledger):** Created within the reactive
chain using `Span.current()` as parent, which reads from the Vert.x
duplicated context. No explicit parent passing needed.

**Guard:** Qhorus does not depend on `quarkus-opentelemetry` — when no OTel
SDK is present, `Tracer` is no-op, `Span.current()` returns a no-op span,
and all of the above is zero-cost.

## Implementation Location

Tracing code goes directly into existing service classes:

| Service | What |
|---------|------|
| `MessageService.dispatch()` | dispatch span + enforcement events |
| `ReactiveMessageService.dispatch()` | reactive dispatch span |
| `CommitmentService.*()` | per-transition spans (open, acknowledge, fulfill, decline, fail, delegate, expireOverdue, extendDeadline) |
| `ReactiveCommitmentService.*()` | reactive per-transition spans |
| `ChannelGateway.fanOut()` | fanout span + per-backend child spans |
| `ChannelGateway.deliverRemote()` | remote delivery span |
| `DeliveryService.deliverPending()` | AT_LEAST_ONCE delivery pump span |
| `LedgerWriteService.record()` | ledger write span + cross-request links |
| `ReactiveLedgerWriteService.record()` | reactive ledger write span |

**Blocking + reactive parity:** both stacks get identical instrumentation.
Quarkus OTel extension handles reactive context propagation via Vert.x
`ContextStorage`. The reactive span lifecycle pattern (see above) manages
span creation and completion across Mutiny operator boundaries.

**New file:** `runtime/src/main/java/io/casehub/qhorus/runtime/config/QhorusTracingConfig.java`

**No changes to:** `api/`, `deployment/`, `connector-backend/`,
`slack-channel/`, `postgres-broadcaster/`, `persistence-memory/`, `testing/`

## Module Impact

Optional modules (`connector-backend`, `slack-channel`) trace automatically —
they go through `MessageService.dispatch()` which traces for them.

`examples/type-system/` gains `opentelemetry-sdk-testing` test-scope to verify
span creation in CI.

## Test Approach

**Unit tests** use `opentelemetry-sdk-testing` (in-memory span exporter):
- Assert span creation with correct names and attributes
- Assert parent-child relationships (dispatch → commitment, dispatch → fanout)
- Assert per-backend child spans in fanout with context propagation
- Assert span events (enforcement checks, observer dispatch scheduling)
- Assert span links for cross-request correlation
- Assert no spans created when `tracing.enabled=false`
- Assert LAST_WRITE dispatch path produces span with correct semantic attribute
- Assert expireOverdue creates root span (no parent)
- Assert delivery spans for deliverRemote and DeliveryService

**Integration tests** (`@QuarkusTest`) verify end-to-end span creation through
the full dispatch pipeline with the in-memory exporter.

## Native Image

`opentelemetry-api` is GraalVM-safe (pure API jar, no reflection). The
`quarkus-opentelemetry` extension handles native image registration for the
SDK. Qhorus depends only on the API jar — no `reflect-config.json` needed.
