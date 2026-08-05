# io.casehub.qhorus.api.gateway.OutboundMessage

**Package:** `io.casehub.qhorus.api.gateway`

**Kind:** `record`

## Fields

### `artefactRefs` (`java.util.List<io.casehub.qhorus.api.message.ArtefactRef>`)

### `content` (`java.lang.String`)

### `correlationId` (`java.lang.String`)

### `inReplyTo` (`java.lang.Long`)

### `messageId` (`java.util.UUID`)

### `sender` (`java.lang.String`)

### `senderActorType` (`ActorType`)

### `sequenceId` (`java.lang.Long`)

### `target` (`java.lang.String`)

### `topic` (`java.lang.String`)

### `type` (`io.casehub.qhorus.api.message.MessageType`)

## Record Components

### `artefactRefs` (`java.util.List<io.casehub.qhorus.api.message.ArtefactRef>`)

### `content` (`java.lang.String`)

### `correlationId` (`java.lang.String`)

### `inReplyTo` (`java.lang.Long`)

### `messageId` (`java.util.UUID`)

### `sender` (`java.lang.String`)

### `senderActorType` (`ActorType`)

### `sequenceId` (`java.lang.Long`)

### `target` (`java.lang.String`)

### `topic` (`java.lang.String`)

### `type` (`io.casehub.qhorus.api.message.MessageType`)

## Constructors

### `public OutboundMessage(java.util.UUID messageId, java.lang.Long sequenceId, java.lang.String sender, io.casehub.qhorus.api.message.MessageType type, java.lang.String content, java.lang.String correlationId, java.lang.Long inReplyTo, ActorType senderActorType, java.util.List<io.casehub.qhorus.api.message.ArtefactRef> artefactRefs, java.lang.String target, java.lang.String topic)`

#### Parameters

- `messageId` (`java.util.UUID`)
- `sequenceId` (`java.lang.Long`)
- `sender` (`java.lang.String`)
- `type` (`io.casehub.qhorus.api.message.MessageType`)
- `content` (`java.lang.String`)
- `correlationId` (`java.lang.String`)
- `inReplyTo` (`java.lang.Long`)
- `senderActorType` (`ActorType`)
- `artefactRefs` (`java.util.List<io.casehub.qhorus.api.message.ArtefactRef>`)
- `target` (`java.lang.String`)
- `topic` (`java.lang.String`)

### `public OutboundMessage(java.util.UUID messageId, java.lang.String sender, io.casehub.qhorus.api.message.MessageType type, java.lang.String content, java.lang.String correlationId, java.lang.Long inReplyTo, ActorType senderActorType, java.util.List<io.casehub.qhorus.api.message.ArtefactRef> artefactRefs, java.lang.String target)`

#### Parameters

- `messageId` (`java.util.UUID`)
- `sender` (`java.lang.String`)
- `type` (`io.casehub.qhorus.api.message.MessageType`)
- `content` (`java.lang.String`)
- `correlationId` (`java.lang.String`)
- `inReplyTo` (`java.lang.Long`)
- `senderActorType` (`ActorType`)
- `artefactRefs` (`java.util.List<io.casehub.qhorus.api.message.ArtefactRef>`)
- `target` (`java.lang.String`)

### `public OutboundMessage(java.util.UUID messageId, java.lang.String sender, io.casehub.qhorus.api.message.MessageType type, java.lang.String content, java.lang.String correlationId, java.lang.Long inReplyTo, ActorType senderActorType, java.util.List<io.casehub.qhorus.api.message.ArtefactRef> artefactRefs, java.lang.String target, java.lang.String topic)`

#### Parameters

- `messageId` (`java.util.UUID`)
- `sender` (`java.lang.String`)
- `type` (`io.casehub.qhorus.api.message.MessageType`)
- `content` (`java.lang.String`)
- `correlationId` (`java.lang.String`)
- `inReplyTo` (`java.lang.Long`)
- `senderActorType` (`ActorType`)
- `artefactRefs` (`java.util.List<io.casehub.qhorus.api.message.ArtefactRef>`)
- `target` (`java.lang.String`)
- `topic` (`java.lang.String`)

## Methods

### `public java.util.List<io.casehub.qhorus.api.message.ArtefactRef> artefactRefs()`

### `public java.lang.String content()`

### `public java.lang.String correlationId()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.lang.Long inReplyTo()`

### `public java.util.UUID messageId()`

### `public java.lang.String sender()`

### `public ActorType senderActorType()`

### `public java.lang.Long sequenceId()`

### `public java.lang.String target()`

### `public final java.lang.String toString()`

### `public java.lang.String topic()`

### `public io.casehub.qhorus.api.message.MessageType type()`
