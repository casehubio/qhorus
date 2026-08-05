# io.casehub.qhorus.api.gateway.ChannelInitialisedEvent

**Package:** `io.casehub.qhorus.api.gateway`

**Kind:** `record`

Fired by `io.casehub.qhorus.runtime.gateway.ChannelGateway` when a channel's
gateway registry entry is initialised — either on channel creation or on application
startup (recovery of all persisted channels).

<p>External backends observe this event to register themselves without needing their
own startup recovery logic. The `.recovered` flag distinguishes startup recovery
(`true`) from first-time channel creation or binding update (`false`).

<p>Refs #183.

## Fields

### `channelId` (`java.util.UUID`)

### `channelName` (`java.lang.String`)

### `recovered` (`boolean`)

## Record Components

### `channelId` (`java.util.UUID`)

### `channelName` (`java.lang.String`)

### `recovered` (`boolean`)

## Constructors

### `public ChannelInitialisedEvent(java.util.UUID channelId, java.lang.String channelName, boolean recovered)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `channelName` (`java.lang.String`)
- `recovered` (`boolean`)

## Methods

### `public java.util.UUID channelId()`

### `public java.lang.String channelName()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public boolean recovered()`

### `public final java.lang.String toString()`
