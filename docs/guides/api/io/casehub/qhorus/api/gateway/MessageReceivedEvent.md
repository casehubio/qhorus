# io.casehub.qhorus.api.gateway.MessageReceivedEvent

**Package:** `io.casehub.qhorus.api.gateway`

**Kind:** `record`

## Fields

### `actorType` (`ActorType`)

### `channelId` (`java.util.UUID`)

### `channelName` (`java.lang.String`)

### `content` (`java.lang.String`)

### `correlationId` (`java.lang.String`)

### `messageId` (`java.lang.Long`)

### `messageType` (`io.casehub.qhorus.api.message.MessageType`)

### `occurredAt` (`java.time.Instant`)

### `senderId` (`java.lang.String`)

### `target` (`java.lang.String`)

### `tenancyId` (`java.lang.String`)

### `topic` (`java.lang.String`)

## Record Components

### `actorType` (`ActorType`)

### `channelId` (`java.util.UUID`)

### `channelName` (`java.lang.String`)

### `content` (`java.lang.String`)

### `correlationId` (`java.lang.String`)

### `messageId` (`java.lang.Long`)

### `messageType` (`io.casehub.qhorus.api.message.MessageType`)

### `occurredAt` (`java.time.Instant`)

### `senderId` (`java.lang.String`)

### `target` (`java.lang.String`)

### `tenancyId` (`java.lang.String`)

### `topic` (`java.lang.String`)

## Constructors

### `public MessageReceivedEvent(java.lang.Long messageId, java.lang.String channelName, java.util.UUID channelId, java.lang.String tenancyId, io.casehub.qhorus.api.message.MessageType messageType, java.lang.String senderId, java.lang.String target, ActorType actorType, java.lang.String correlationId, java.time.Instant occurredAt, java.lang.String content, java.lang.String topic)`

#### Parameters

- `messageId` (`java.lang.Long`)
- `channelName` (`java.lang.String`)
- `channelId` (`java.util.UUID`)
- `tenancyId` (`java.lang.String`)
- `messageType` (`io.casehub.qhorus.api.message.MessageType`)
- `senderId` (`java.lang.String`)
- `target` (`java.lang.String`)
- `actorType` (`ActorType`)
- `correlationId` (`java.lang.String`)
- `occurredAt` (`java.time.Instant`)
- `content` (`java.lang.String`)
- `topic` (`java.lang.String`)

## Methods

### `public ActorType actorType()`

### `public java.util.UUID channelId()`

### `public java.lang.String channelName()`

### `public java.lang.String content()`

### `public java.lang.String correlationId()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public static io.casehub.qhorus.api.gateway.MessageReceivedEvent fromMessage(io.casehub.qhorus.api.message.Message message, java.lang.String channelName)`

#### Parameters

- `message` (`io.casehub.qhorus.api.message.Message`)
- `channelName` (`java.lang.String`)

### `public final int hashCode()`

### `public java.lang.Long messageId()`

### `public io.casehub.qhorus.api.message.MessageType messageType()`

### `public java.time.Instant occurredAt()`

### `public java.lang.String senderId()`

### `public java.lang.String target()`

### `public java.lang.String tenancyId()`

### `public final java.lang.String toString()`

### `public java.lang.String topic()`
