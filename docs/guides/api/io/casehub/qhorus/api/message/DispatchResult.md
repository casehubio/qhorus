# io.casehub.qhorus.api.message.DispatchResult

**Package:** `io.casehub.qhorus.api.message`

**Kind:** `record`

## Fields

### `advisories` (`java.util.List<java.lang.String>`)

### `artefactRefs` (`java.util.List<io.casehub.qhorus.api.message.ArtefactRef>`)

### `causedByEntryId` (`java.util.UUID`)

### `channelId` (`java.util.UUID`)

### `correlationId` (`java.lang.String`)

### `inReplyTo` (`java.lang.Long`)

### `ledgerEntryId` (`java.util.UUID`)

### `messageId` (`java.lang.Long`)

### `parentReplyCount` (`int`)

### `sender` (`java.lang.String`)

### `subjectId` (`java.util.UUID`)

### `target` (`java.lang.String`)

### `type` (`io.casehub.qhorus.api.message.MessageType`)

## Record Components

### `advisories` (`java.util.List<java.lang.String>`)

### `artefactRefs` (`java.util.List<io.casehub.qhorus.api.message.ArtefactRef>`)

### `causedByEntryId` (`java.util.UUID`)

### `channelId` (`java.util.UUID`)

### `correlationId` (`java.lang.String`)

### `inReplyTo` (`java.lang.Long`)

### `ledgerEntryId` (`java.util.UUID`)

### `messageId` (`java.lang.Long`)

### `parentReplyCount` (`int`)

### `sender` (`java.lang.String`)

### `subjectId` (`java.util.UUID`)

### `target` (`java.lang.String`)

### `type` (`io.casehub.qhorus.api.message.MessageType`)

## Constructors

### `public DispatchResult(java.lang.Long messageId, java.util.UUID channelId, java.lang.String sender, io.casehub.qhorus.api.message.MessageType type, java.lang.String correlationId, java.lang.Long inReplyTo, java.util.List<io.casehub.qhorus.api.message.ArtefactRef> artefactRefs, java.lang.String target, java.util.UUID ledgerEntryId, java.util.UUID subjectId, java.util.UUID causedByEntryId, int parentReplyCount, java.util.List<java.lang.String> advisories)`

#### Parameters

- `messageId` (`java.lang.Long`)
- `channelId` (`java.util.UUID`)
- `sender` (`java.lang.String`)
- `type` (`io.casehub.qhorus.api.message.MessageType`)
- `correlationId` (`java.lang.String`)
- `inReplyTo` (`java.lang.Long`)
- `artefactRefs` (`java.util.List<io.casehub.qhorus.api.message.ArtefactRef>`)
- `target` (`java.lang.String`)
- `ledgerEntryId` (`java.util.UUID`)
- `subjectId` (`java.util.UUID`)
- `causedByEntryId` (`java.util.UUID`)
- `parentReplyCount` (`int`)
- `advisories` (`java.util.List<java.lang.String>`)

## Methods

### `public java.util.List<java.lang.String> advisories()`

### `public java.util.List<io.casehub.qhorus.api.message.ArtefactRef> artefactRefs()`

### `public java.util.UUID causedByEntryId()`

### `public java.util.UUID channelId()`

### `public java.lang.String correlationId()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.lang.Long inReplyTo()`

### `public java.util.UUID ledgerEntryId()`

### `public java.lang.Long messageId()`

### `public int parentReplyCount()`

### `public java.lang.String sender()`

### `public java.util.UUID subjectId()`

### `public java.lang.String target()`

### `public final java.lang.String toString()`

### `public io.casehub.qhorus.api.message.MessageType type()`
