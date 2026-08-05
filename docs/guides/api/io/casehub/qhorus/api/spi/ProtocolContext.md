# io.casehub.qhorus.api.spi.ProtocolContext

**Package:** `io.casehub.qhorus.api.spi`

**Kind:** `record`

## Fields

### `activeCommitments` (`java.util.List<io.casehub.qhorus.api.message.Commitment>`)

### `channelId` (`java.util.UUID`)

### `channelName` (`java.lang.String`)

### `correlationId` (`java.lang.String`)

### `incomingType` (`io.casehub.qhorus.api.message.MessageType`)

### `protocolParticipants` (`java.util.List<java.lang.String>`)

### `recentMessages` (`java.util.List<io.casehub.qhorus.api.message.MessageView>`)

### `sender` (`java.lang.String`)

## Record Components

### `activeCommitments` (`java.util.List<io.casehub.qhorus.api.message.Commitment>`)

### `channelId` (`java.util.UUID`)

### `channelName` (`java.lang.String`)

### `correlationId` (`java.lang.String`)

### `incomingType` (`io.casehub.qhorus.api.message.MessageType`)

### `protocolParticipants` (`java.util.List<java.lang.String>`)

### `recentMessages` (`java.util.List<io.casehub.qhorus.api.message.MessageView>`)

### `sender` (`java.lang.String`)

## Constructors

### `public ProtocolContext(java.util.UUID channelId, java.lang.String channelName, io.casehub.qhorus.api.message.MessageType incomingType, java.lang.String sender, java.lang.String correlationId, java.util.List<java.lang.String> protocolParticipants, java.util.List<io.casehub.qhorus.api.message.MessageView> recentMessages, java.util.List<io.casehub.qhorus.api.message.Commitment> activeCommitments)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `channelName` (`java.lang.String`)
- `incomingType` (`io.casehub.qhorus.api.message.MessageType`)
- `sender` (`java.lang.String`)
- `correlationId` (`java.lang.String`)
- `protocolParticipants` (`java.util.List<java.lang.String>`)
- `recentMessages` (`java.util.List<io.casehub.qhorus.api.message.MessageView>`)
- `activeCommitments` (`java.util.List<io.casehub.qhorus.api.message.Commitment>`)

## Methods

### `public java.util.List<io.casehub.qhorus.api.message.Commitment> activeCommitments()`

### `public java.util.UUID channelId()`

### `public java.lang.String channelName()`

### `public java.lang.String correlationId()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public io.casehub.qhorus.api.message.MessageType incomingType()`

### `public java.util.List<java.lang.String> protocolParticipants()`

### `public java.util.List<io.casehub.qhorus.api.message.MessageView> recentMessages()`

### `public java.lang.String sender()`

### `public final java.lang.String toString()`
