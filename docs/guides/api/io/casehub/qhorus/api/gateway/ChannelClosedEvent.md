# io.casehub.qhorus.api.gateway.ChannelClosedEvent

**Package:** `io.casehub.qhorus.api.gateway`

**Kind:** `record`

Fired by `io.casehub.qhorus.runtime.gateway.ChannelGateway` after all backends
have been closed for a channel. Enables observers (e.g. webhook registry, external
integrations) to clean up channel-specific state without implementing ChannelBackend.

<p>Mirrors the existing `ChannelInitialisedEvent` pattern.

<p>Refs #345.

## Fields

### `channelId` (`java.util.UUID`)

### `channelName` (`java.lang.String`)

## Record Components

### `channelId` (`java.util.UUID`)

### `channelName` (`java.lang.String`)

## Constructors

### `public ChannelClosedEvent(java.util.UUID channelId, java.lang.String channelName)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `channelName` (`java.lang.String`)

## Methods

### `public java.util.UUID channelId()`

### `public java.lang.String channelName()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public final java.lang.String toString()`
