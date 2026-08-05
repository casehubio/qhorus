# io.casehub.qhorus.api.gateway.InboundHumanMessage

**Package:** `io.casehub.qhorus.api.gateway`

**Kind:** `record`

## Fields

### `content` (`java.lang.String`)

### `correlationId` (`java.lang.String`)

### `externalSenderId` (`java.lang.String`)

### `inReplyTo` (`java.lang.Long`)

### `metadata` (`java.util.Map<java.lang.String,java.lang.String>`)

### `receivedAt` (`java.time.Instant`)

## Record Components

### `content` (`java.lang.String`)

### `correlationId` (`java.lang.String`)

### `externalSenderId` (`java.lang.String`)

### `inReplyTo` (`java.lang.Long`)

### `metadata` (`java.util.Map<java.lang.String,java.lang.String>`)

### `receivedAt` (`java.time.Instant`)

## Constructors

### `public InboundHumanMessage(java.lang.String externalSenderId, java.lang.String content, java.time.Instant receivedAt, java.util.Map<java.lang.String,java.lang.String> metadata, java.lang.String correlationId, java.lang.Long inReplyTo)`

#### Parameters

- `externalSenderId` (`java.lang.String`)
- `content` (`java.lang.String`)
- `receivedAt` (`java.time.Instant`)
- `metadata` (`java.util.Map<java.lang.String,java.lang.String>`)
- `correlationId` (`java.lang.String`)
- `inReplyTo` (`java.lang.Long`)

## Methods

### `public java.lang.String content()`

### `public java.lang.String correlationId()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public java.lang.String externalSenderId()`

### `public final int hashCode()`

### `public java.lang.Long inReplyTo()`

### `public java.util.Map<java.lang.String,java.lang.String> metadata()`

### `public java.time.Instant receivedAt()`

### `public final java.lang.String toString()`
