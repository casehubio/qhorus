# io.casehub.qhorus.api.message.MessageDispatch

**Package:** `io.casehub.qhorus.api.message`

**Kind:** `record`

## Fields

### `actorType` (`ActorType`)

### `artefactRefs` (`java.util.List<io.casehub.qhorus.api.message.ArtefactRef>`)

### `causedByEntryId` (`java.util.UUID`)

### `channelId` (`java.util.UUID`)

### `content` (`java.lang.String`)

### `correlationId` (`java.lang.String`)

### `deadline` (`java.time.Instant`)

### `inReplyTo` (`java.lang.Long`)

### `sender` (`java.lang.String`)

### `subjectId` (`java.util.UUID`)

### `target` (`java.lang.String`)

### `telemetry` (`java.lang.String`)

### `tenancyId` (`java.lang.String`)

### `topic` (`java.lang.String`)

### `type` (`io.casehub.qhorus.api.message.MessageType`)

## Record Components

### `actorType` (`ActorType`)

### `artefactRefs` (`java.util.List<io.casehub.qhorus.api.message.ArtefactRef>`)

### `causedByEntryId` (`java.util.UUID`)

### `channelId` (`java.util.UUID`)

### `content` (`java.lang.String`)

### `correlationId` (`java.lang.String`)

### `deadline` (`java.time.Instant`)

### `inReplyTo` (`java.lang.Long`)

### `sender` (`java.lang.String`)

### `subjectId` (`java.util.UUID`)

### `target` (`java.lang.String`)

### `telemetry` (`java.lang.String`)

### `tenancyId` (`java.lang.String`)

### `topic` (`java.lang.String`)

### `type` (`io.casehub.qhorus.api.message.MessageType`)

## Constructors

### `public MessageDispatch(java.util.UUID channelId, java.lang.String sender, io.casehub.qhorus.api.message.MessageType type, java.lang.String content, java.lang.String correlationId, java.lang.Long inReplyTo, java.util.List<io.casehub.qhorus.api.message.ArtefactRef> artefactRefs, java.lang.String target, java.util.UUID subjectId, java.util.UUID causedByEntryId, ActorType actorType, java.time.Instant deadline, java.lang.String telemetry, java.lang.String tenancyId, java.lang.String topic)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `sender` (`java.lang.String`)
- `type` (`io.casehub.qhorus.api.message.MessageType`)
- `content` (`java.lang.String`)
- `correlationId` (`java.lang.String`)
- `inReplyTo` (`java.lang.Long`)
- `artefactRefs` (`java.util.List<io.casehub.qhorus.api.message.ArtefactRef>`)
- `target` (`java.lang.String`)
- `subjectId` (`java.util.UUID`)
- `causedByEntryId` (`java.util.UUID`)
- `actorType` (`ActorType`)
- `deadline` (`java.time.Instant`)
- `telemetry` (`java.lang.String`)
- `tenancyId` (`java.lang.String`)
- `topic` (`java.lang.String`)

## Methods

### `public ActorType actorType()`

### `public java.util.List<io.casehub.qhorus.api.message.ArtefactRef> artefactRefs()`

### `public static io.casehub.qhorus.api.message.MessageDispatch.Builder builder()`

### `public java.util.UUID causedByEntryId()`

### `public java.util.UUID channelId()`

### `public java.lang.String content()`

### `public java.lang.String correlationId()`

### `public java.time.Instant deadline()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.lang.Long inReplyTo()`

### `public java.lang.String sender()`

### `public java.util.UUID subjectId()`

### `public java.lang.String target()`

### `public java.lang.String telemetry()`

### `public java.lang.String tenancyId()`

### `public final java.lang.String toString()`

### `public java.lang.String topic()`

### `public io.casehub.qhorus.api.message.MessageType type()`
