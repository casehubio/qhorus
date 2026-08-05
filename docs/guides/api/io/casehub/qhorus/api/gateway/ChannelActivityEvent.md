# io.casehub.qhorus.api.gateway.ChannelActivityBroadcaster.ChannelActivityEvent

**Package:** `io.casehub.qhorus.api.gateway`

**Kind:** `record`

Channel activity event.

## Fields

### `channelId` (`java.util.UUID`)

### `channelName` (`java.lang.String`)

### `messageId` (`java.lang.Long`)

## Record Components

### `channelId` (`java.util.UUID`)

the channel UUID

### `channelName` (`java.lang.String`)

the channel name

### `messageId` (`java.lang.Long`)

the message primary key

## Constructors

### `public ChannelActivityEvent(java.util.UUID channelId, java.lang.String channelName, java.lang.Long messageId)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `channelName` (`java.lang.String`)
- `messageId` (`java.lang.Long`)

## Methods

### `public java.util.UUID channelId()`

### `public java.lang.String channelName()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.lang.Long messageId()`

### `public final java.lang.String toString()`
