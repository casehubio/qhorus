# io.casehub.qhorus.api.message.MessageView

**Package:** `io.casehub.qhorus.api.message`

**Kind:** `record`

## Fields

### `actorType` (`ActorType`)

### `artefactRefs` (`java.util.List<io.casehub.qhorus.api.message.ArtefactRef>`)

### `channelId` (`java.util.UUID`)

### `content` (`java.lang.String`)

### `correlationId` (`java.lang.String`)

### `createdAt` (`java.time.Instant`)

### `deadline` (`java.time.Instant`)

### `id` (`java.lang.Long`)

### `inReplyTo` (`java.lang.Long`)

### `replyCount` (`int`)

### `sender` (`java.lang.String`)

### `target` (`java.lang.String`)

### `topic` (`java.lang.String`)

### `type` (`io.casehub.qhorus.api.message.MessageType`)

## Record Components

### `actorType` (`ActorType`)

### `artefactRefs` (`java.util.List<io.casehub.qhorus.api.message.ArtefactRef>`)

### `channelId` (`java.util.UUID`)

### `content` (`java.lang.String`)

### `correlationId` (`java.lang.String`)

### `createdAt` (`java.time.Instant`)

### `deadline` (`java.time.Instant`)

### `id` (`java.lang.Long`)

### `inReplyTo` (`java.lang.Long`)

### `replyCount` (`int`)

### `sender` (`java.lang.String`)

### `target` (`java.lang.String`)

### `topic` (`java.lang.String`)

### `type` (`io.casehub.qhorus.api.message.MessageType`)

## Constructors

### `public MessageView(java.lang.Long id, java.util.UUID channelId, java.lang.String sender, io.casehub.qhorus.api.message.MessageType type, java.lang.String content, java.lang.String correlationId, java.lang.Long inReplyTo, java.lang.String target, java.lang.String topic, java.util.List<io.casehub.qhorus.api.message.ArtefactRef> artefactRefs, ActorType actorType, java.time.Instant createdAt, java.time.Instant deadline, int replyCount)`

#### Parameters

- `id` (`java.lang.Long`)
- `channelId` (`java.util.UUID`)
- `sender` (`java.lang.String`)
- `type` (`io.casehub.qhorus.api.message.MessageType`)
- `content` (`java.lang.String`)
- `correlationId` (`java.lang.String`)
- `inReplyTo` (`java.lang.Long`)
- `target` (`java.lang.String`)
- `topic` (`java.lang.String`)
- `artefactRefs` (`java.util.List<io.casehub.qhorus.api.message.ArtefactRef>`)
- `actorType` (`ActorType`)
- `createdAt` (`java.time.Instant`)
- `deadline` (`java.time.Instant`)
- `replyCount` (`int`)

## Methods

### `public ActorType actorType()`

### `public java.util.List<io.casehub.qhorus.api.message.ArtefactRef> artefactRefs()`

### `public java.util.UUID channelId()`

### `public java.lang.String content()`

### `public java.lang.String correlationId()`

### `public java.time.Instant createdAt()`

### `public java.time.Instant deadline()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.lang.Long id()`

### `public java.lang.Long inReplyTo()`

### `public int replyCount()`

### `public java.lang.String sender()`

### `public java.lang.String target()`

### `public final java.lang.String toString()`

### `public java.lang.String topic()`

### `public io.casehub.qhorus.api.message.MessageType type()`
