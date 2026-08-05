# io.casehub.qhorus.api.gateway.NormalisedMessage

**Package:** `io.casehub.qhorus.api.gateway`

**Kind:** `record`

## Fields

### `artefactRefs` (`java.util.List<io.casehub.qhorus.api.message.ArtefactRef>`)

### `content` (`java.lang.String`)

### `correlationId` (`java.lang.String`)

### `inReplyTo` (`java.lang.Long`)

### `senderInstanceId` (`java.lang.String`)

### `target` (`java.lang.String`)

### `type` (`io.casehub.qhorus.api.message.MessageType`)

## Record Components

### `artefactRefs` (`java.util.List<io.casehub.qhorus.api.message.ArtefactRef>`)

### `content` (`java.lang.String`)

### `correlationId` (`java.lang.String`)

### `inReplyTo` (`java.lang.Long`)

### `senderInstanceId` (`java.lang.String`)

### `target` (`java.lang.String`)

### `type` (`io.casehub.qhorus.api.message.MessageType`)

## Constructors

### `public NormalisedMessage(io.casehub.qhorus.api.message.MessageType type, java.lang.String content, java.lang.String senderInstanceId, java.lang.String correlationId, java.lang.Long inReplyTo, java.util.List<io.casehub.qhorus.api.message.ArtefactRef> artefactRefs, java.lang.String target)`

#### Parameters

- `type` (`io.casehub.qhorus.api.message.MessageType`)
- `content` (`java.lang.String`)
- `senderInstanceId` (`java.lang.String`)
- `correlationId` (`java.lang.String`)
- `inReplyTo` (`java.lang.Long`)
- `artefactRefs` (`java.util.List<io.casehub.qhorus.api.message.ArtefactRef>`)
- `target` (`java.lang.String`)

## Methods

### `public java.util.List<io.casehub.qhorus.api.message.ArtefactRef> artefactRefs()`

### `public java.lang.String content()`

### `public java.lang.String correlationId()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.lang.Long inReplyTo()`

### `public java.lang.String senderInstanceId()`

### `public java.lang.String target()`

### `public final java.lang.String toString()`

### `public io.casehub.qhorus.api.message.MessageType type()`
