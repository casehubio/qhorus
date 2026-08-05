# io.casehub.qhorus.api.gateway.MessageObserver

**Package:** `io.casehub.qhorus.api.gateway`

**Kind:** `interface`

Transport-agnostic SPI for receiving notification of every persisted qhorus
message. All 9 speech-act types fire.

<p>`MessageReceivedEvent.content()` is always `null` for
`io.casehub.qhorus.api.message.MessageType.EVENT` — EVENT is a signal type;
use `STATUS` for content-bearing observe-channel broadcasts.

<p>Implementations may carry additional CDI qualifiers (e.g. `@Named`, custom
application qualifiers) — the dispatcher discovers all implementations via
`@Any Instance<MessageObserver>` regardless of additional qualifiers.

<p>Multiple implementations may coexist as CDI beans — the runtime iterates
all of them. `Scope.LOCAL` is the fast path (in-JVM, CDI); declare
`Scope.CLUSTER` for network-crossing transports (Kafka, WebSocket, etc.).

<p><strong>Scope:</strong> any normal CDI scope is valid (`@ApplicationScoped`,
`@RequestScoped`, etc.). The dispatcher closes each
`jakarta.enterprise.inject.Instance.Handle` in a `finally` block,
correctly destroying `@Dependent`-scoped implementations after each dispatch.

<p><strong>Do not query qhorus message state</strong> in observer implementations.
The dispatcher fires before the enclosing transaction commits; querying the message
store may yield stale or absent data. The `MessageReceivedEvent` payload is
self-contained. JTA after-commit dispatch is tracked in qhorus#166.

<p>Implementations must not propagate exceptions — the runtime logs and
continues regardless.

## Methods

### `public default java.util.Set<java.lang.String> channels()`

Opt-in channel filter. An empty set (the default) means the observer receives
messages from every channel. A non-empty set limits delivery to exact channel
name matches only.

<p>Channel names are the stable routing key — consistent with
`MessageReceivedEvent.channelName()`.

<p>Refs #164.

### `public abstract void onMessage(io.casehub.qhorus.api.gateway.MessageReceivedEvent event)`

#### Parameters

- `event` (`io.casehub.qhorus.api.gateway.MessageReceivedEvent`)

### `public default io.casehub.qhorus.api.gateway.MessageObserver.Scope scope()`
