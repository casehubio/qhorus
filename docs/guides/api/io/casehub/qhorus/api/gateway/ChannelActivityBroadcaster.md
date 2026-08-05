# io.casehub.qhorus.api.gateway.ChannelActivityBroadcaster

**Package:** `io.casehub.qhorus.api.gateway`

**Kind:** `interface`

SPI for broadcasting channel activity events.

<p>Implementations can notify external systems (e.g., SSE streams,
WebSocket channels) when new messages are dispatched to a channel.

<p>Default implementation is a no-op. Override by providing a CDI bean
with `@Alternative @Priority(100)` or higher.

<p>Refs #162.

## Methods

### `public abstract void broadcast(io.casehub.qhorus.api.gateway.ChannelActivityBroadcaster.ChannelActivityEvent event)`

Broadcast a channel activity event.

#### Parameters

- `event` (`io.casehub.qhorus.api.gateway.ChannelActivityBroadcaster.ChannelActivityEvent`) — the activity event to broadcast
