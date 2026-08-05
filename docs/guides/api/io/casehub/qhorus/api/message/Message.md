# io.casehub.qhorus.api.message.Message

**Package:** `io.casehub.qhorus.api.message`

**Kind:** `record`

## Fields

### `acknowledgedAt` (`java.time.Instant`)

### `actorType` (`ActorType`)

### `artefactRefs` (`java.util.List<io.casehub.qhorus.api.message.ArtefactRef>`)

### `channelId` (`java.util.UUID`)

### `commitmentId` (`java.util.UUID`)

### `content` (`java.lang.String`)

### `correlationId` (`java.lang.String`)

### `createdAt` (`java.time.Instant`)

### `deadline` (`java.time.Instant`)

### `id` (`java.lang.Long`)

### `inReplyTo` (`java.lang.Long`)

### `messageType` (`io.casehub.qhorus.api.message.MessageType`)

### `replyCount` (`int`)

### `sender` (`java.lang.String`)

### `target` (`java.lang.String`)

### `tenancyId` (`java.lang.String`)

### `topic` (`java.lang.String`)

### `version` (`int`)

## Record Components

### `acknowledgedAt` (`java.time.Instant`)

### `actorType` (`ActorType`)

### `artefactRefs` (`java.util.List<io.casehub.qhorus.api.message.ArtefactRef>`)

### `channelId` (`java.util.UUID`)

### `commitmentId` (`java.util.UUID`)

### `content` (`java.lang.String`)

### `correlationId` (`java.lang.String`)

### `createdAt` (`java.time.Instant`)

### `deadline` (`java.time.Instant`)

### `id` (`java.lang.Long`)

### `inReplyTo` (`java.lang.Long`)

### `messageType` (`io.casehub.qhorus.api.message.MessageType`)

### `replyCount` (`int`)

### `sender` (`java.lang.String`)

### `target` (`java.lang.String`)

### `tenancyId` (`java.lang.String`)

### `topic` (`java.lang.String`)

### `version` (`int`)

## Constructors

### `public Message(java.lang.Long id, java.util.UUID channelId, java.lang.String sender, io.casehub.qhorus.api.message.MessageType messageType, ActorType actorType, java.lang.String tenancyId, java.lang.String content, java.lang.String correlationId, java.lang.Long inReplyTo, int replyCount, java.util.List<io.casehub.qhorus.api.message.ArtefactRef> artefactRefs, java.lang.String target, java.lang.String topic, java.util.UUID commitmentId, java.time.Instant deadline, java.time.Instant acknowledgedAt, int version, java.time.Instant createdAt)`

#### Parameters

- `id` (`java.lang.Long`)
- `channelId` (`java.util.UUID`)
- `sender` (`java.lang.String`)
- `messageType` (`io.casehub.qhorus.api.message.MessageType`)
- `actorType` (`ActorType`)
- `tenancyId` (`java.lang.String`)
- `content` (`java.lang.String`)
- `correlationId` (`java.lang.String`)
- `inReplyTo` (`java.lang.Long`)
- `replyCount` (`int`)
- `artefactRefs` (`java.util.List<io.casehub.qhorus.api.message.ArtefactRef>`)
- `target` (`java.lang.String`)
- `topic` (`java.lang.String`)
- `commitmentId` (`java.util.UUID`)
- `deadline` (`java.time.Instant`)
- `acknowledgedAt` (`java.time.Instant`)
- `version` (`int`)
- `createdAt` (`java.time.Instant`)

## Methods

### `public java.time.Instant acknowledgedAt()`

### `public ActorType actorType()`

### `public java.util.List<io.casehub.qhorus.api.message.ArtefactRef> artefactRefs()`

### `public static io.casehub.qhorus.api.message.Message.Builder builder()`

### `public java.util.UUID channelId()`

### `public java.util.UUID commitmentId()`

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

### `public io.casehub.qhorus.api.message.MessageType messageType()`

### `public int replyCount()`

### `public java.lang.String sender()`

### `public java.lang.String target()`

### `public java.lang.String tenancyId()`

### `public io.casehub.qhorus.api.message.Message.Builder toBuilder()`

### `public final java.lang.String toString()`

### `public java.lang.String topic()`

### `public int version()`
