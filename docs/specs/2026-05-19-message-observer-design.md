# Design Spec — MessageObserver SPI and InProcessMessageBus (#153)

**Epic:** epic-153-cdi-message-event
**Issue:** casehubio/qhorus#153
**Date:** 2026-05-19
**Architecture doc:** `docs/messaging-architecture.md`

---

## Problem

Harness applications (clinical, aml, devtown) need to react when a message
arrives on a qhorus channel — without polling. The only current options are
`check_messages` (polling) or Watchdog (scheduled scan). Neither is event-driven.

The immediate blocker: clinical's PI governance flow (casehubio/clinical#5) sends
a COMMAND to a PI, then needs to act when the PI responds with DONE or DECLINE.
Without a push notification, clinical must either poll or install a bypass REST
endpoint that corrupts the normative record by recording the PI decision outside
the channel.

---

## Design Decision

**`MessageObserver` SPI with `InProcessMessageBus` as the CDI default.**

The full design rationale — including why CDI alone is insufficient for a
distributed mesh, the LOCAL/CLUSTER scope model, and the Vert.x/EIP pattern
references — is in `docs/messaging-architecture.md`. This spec covers the
implementation details.

---

## New Types: `casehub-qhorus-api`

### `MessageReceivedEvent`

Plain Java record. No CDI dependency. The canonical event payload for all
`MessageObserver` implementations.

```java
public record MessageReceivedEvent(
    String   channelName,    // for name-based routing (e.g. clinical parses deviationId)
    UUID     channelId,      // for reliable programmatic lookup
    MessageType messageType,
    String   senderId,
    String   correlationId,  // nullable — correlates response to originating COMMAND
    String   content         // null for EVENT (PP-20260508-90428f)
) {}
```

`content` is explicitly null for `MessageType.EVENT`. `LedgerWriteService` already
extracts EVENT telemetry into dedicated columns; the event payload follows the same
rule. Implementations must not attempt to render or relay EVENT content.

### `MessageObserver`

Transport-agnostic SPI. Functional interface so lambda registration is possible.

```java
@FunctionalInterface
public interface MessageObserver {
    void onMessage(MessageReceivedEvent event);

    default Scope scope() { return Scope.LOCAL; }

    enum Scope { LOCAL, CLUSTER }
}
```

`LOCAL` — delivery within the same JVM, no serialisation, no network.
`CLUSTER` — cross-process delivery; implementation is responsible for transport.

`scope()` is informational for the current implementation. `MessageService` does
not filter by scope — it calls all registered observers regardless. The declaration
enables future optimisation (e.g. skip serialisation for LOCAL observers) and
makes deployment intent explicit for operators.

---

## New Types: `casehub-qhorus` runtime

### `InProcessMessageBus`

The `@DefaultBean` implementation. Fires a CDI event asynchronously. This is the
fast path for all embedded harnesses.

```java
@DefaultBean
@ApplicationScoped
public class InProcessMessageBus implements MessageObserver {

    @Inject
    Event<MessageReceivedEvent> cdiEvent;

    @Override
    public void onMessage(MessageReceivedEvent event) {
        cdiEvent.fireAsync(event)
                .exceptionally(t -> {
                    LOG.warnf("CDI observer failed for channel '%s' type %s: %s",
                              event.channelName(), event.messageType(), t.getMessage());
                    return null;
                });
    }
}
```

Active by default in all embedded harnesses. Overridden (if desired) by registering
an `@Alternative @Priority(1)` bean. Additional CLUSTER implementations coexist
alongside it — `Instance<MessageObserver>` iterates all registered beans.

Harness code observes via:

```java
void onMessage(@ObservesAsync MessageReceivedEvent event) {
    if (event.messageType() != MessageType.DONE &&
        event.messageType() != MessageType.DECLINE) return;
    // ...
}
```

---

## Changes: `MessageService.send()`

### Channel lookup — always fetch

Currently the channel is looked up conditionally (only if `allowedTypes` is set,
via `ifPresent`). Change to always fetch and store the result — needed to populate
`channelName` in the event without an extra query:

```java
// Before (conditional)
channelService.findById(channelId).ifPresent(ch -> messageTypePolicy.validate(ch, type));

// After (always fetch, reuse for event)
Channel ch = channelService.findById(channelId).orElse(null);
if (ch != null) messageTypePolicy.validate(ch, type);
```

The channel is in the Hibernate L1 cache within the transaction; the extra
`orElse(null)` branch has no practical overhead. `channelName` will be null only
if the channel is deleted mid-transaction (not a realistic path).

### Observer dispatch — after persistence

After `messageStore.put(message)` and before `channelService.updateLastActivity()`:

```java
dispatchObservers(ch, message);
```

Where `dispatchObservers` is a private method:

```java
private void dispatchObservers(Channel ch, Message message) {
    String content = message.messageType == MessageType.EVENT
                     ? null : message.content;
    MessageReceivedEvent event = new MessageReceivedEvent(
        ch != null ? ch.name : null,
        channelId,
        message.messageType,
        message.sender,
        message.correlationId,
        content
    );
    for (MessageObserver observer : observers) {
        try {
            observer.onMessage(event);
        } catch (Exception e) {
            LOG.warnf("MessageObserver %s failed for channel '%s': %s",
                      observer.getClass().getSimpleName(),
                      event.channelName(), e.getMessage());
        }
    }
}
```

Failures are non-fatal, non-propagating. Each observer is independently wrapped.
A broken observer does not affect persistence, ledger, fan-out, or any other observer.

### Full injection in `MessageService`

```java
@Inject Instance<MessageObserver> observers;
```

---

## Changes: `ReactiveMessageService.send()`

Reactive parity is required. An application running with
`quarkus.datasource.qhorus.reactive=true` uses `ReactiveMessageService` exclusively;
skipping observer dispatch would silently break all CDI event delivery for reactive
stacks.

The dispatch hooks into the reactive chain after the transaction commits:

```java
return Panache.withTransaction(() ->
    // ... persist, commitment, lastActivity ...
    .invoke(message -> dispatchObservers(ch, message))  // synchronous, non-blocking for CDI
);
```

`dispatchObservers` is the same logic as the blocking path — extracted to a shared
`MessageObserverDispatcher` helper (package-private) to avoid duplication.

### `MessageObserverDispatcher` (shared helper)

```java
class MessageObserverDispatcher {
    private final Instance<MessageObserver> observers;

    MessageObserverDispatcher(Instance<MessageObserver> observers) {
        this.observers = observers;
    }

    void dispatch(Channel ch, Message message) {
        String content = message.messageType == MessageType.EVENT
                         ? null : message.content;
        MessageReceivedEvent event = new MessageReceivedEvent(
            ch != null ? ch.name : null, ch != null ? ch.id : message.channelId,
            message.messageType, message.sender, message.correlationId, content);
        for (MessageObserver observer : observers) {
            try { observer.onMessage(event); }
            catch (Exception e) {
                LOG.warnf("MessageObserver %s failed: %s",
                          observer.getClass().getSimpleName(), e.getMessage());
            }
        }
    }
}
```

Both `MessageService` and `ReactiveMessageService` inject this helper via CDI.

---

## Testing

### Unit: `MessageObserverDispatcherTest`

No CDI, no database. Direct wiring via constructor.

- `dispatch_allNineTypes_firesToAllObservers` — 9 messages, assert observer called 9 times
- `dispatch_eventType_contentIsNull` — send EVENT with content, assert `event.content()` is null
- `dispatch_nonEventType_contentPreserved` — send COMMAND with content, assert content preserved
- `dispatch_observerThrows_otherObserversStillFire` — two observers, first throws, assert second fires
- `dispatch_nullChannel_channelNameIsNull` — defensive case
- `dispatch_correlationId_passedThrough` — nullable field round-trips correctly

### Integration: `MessageObserverIntegrationTest`

`@QuarkusTest`. Registers a test-local `MessageObserver` alternative, sends via
`QhorusMcpTools.sendMessage()`, asserts the observer fires with correct fields.

- `sendMessage_firesObserver_withCorrectChannelAndType`
- `sendMessage_eventType_observerReceivesNullContent`
- `sendMessage_withCorrelationId_observerReceivesCorrelationId`

The test-local observer is an inner `@ApplicationScoped @Alternative @Priority(1)`
class that captures events into a `CopyOnWriteArrayList`. Reset between tests via
`@BeforeEach`.

**Note:** `@QuarkusTest` classes in the current runtime module fail at CDI discovery
due to a pre-existing `ReactiveLedgerEntryRepository` CDI issue (unrelated to #153).
If this blocks, write integration tests in `testing/` module using `InMemory*Store`
stubs for full isolation.

### `ToolOverloadDiscoverabilityTest`

No changes — `dispatchObservers` is `private`, not a `@Tool` method. No risk of
overload shadowing.

---

## Deferred Concerns

| Concern | Issue |
|---------|-------|
| CLUSTER transport implementations (Kafka, WebSocket, Webhook) | casehubio/qhorus#163 |
| Per-channel subscription scope on `MessageObserver` | casehubio/qhorus#164 |
| SmallRye / MicroProfile Reactive Messaging bridge | casehubio/qhorus#165 |
| Multi-node embedded fleet cross-node delivery gap | casehubio/qhorus#162 |
| Type inference in `DefaultInboundNormaliser` (QUERY when correlationId non-null) | casehubio/qhorus#158 |

---

## Out of Scope

- Any `CLUSTER`-scoped implementation — SPI only in this epic
- Changes to `HumanParticipatingChannelBackend` / `ChannelGateway` inbound path —
  `MessageService.send()` is the universal point; no gateway changes needed
- Changes to `QhorusMcpTools.sendMessage()` — dispatch happens in `MessageService`,
  not in the MCP layer (unlike `LedgerWriteService.record()`)
- Watchdog or scheduled observer invocation — `MessageObserver` is synchronous
  post-persistence, not time-driven
