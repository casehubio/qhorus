# io.casehub.qhorus.api.message.Commitment

**Package:** `io.casehub.qhorus.api.message`

**Kind:** `record`

## Fields

### `acknowledgedAt` (`java.time.Instant`)

### `channelId` (`java.util.UUID`)

### `correlationId` (`java.lang.String`)

### `createdAt` (`java.time.Instant`)

### `delegatedTo` (`java.lang.String`)

### `expiresAt` (`java.time.Instant`)

### `id` (`java.util.UUID`)

### `messageType` (`io.casehub.qhorus.api.message.MessageType`)

### `obligor` (`java.lang.String`)

### `parentCommitmentId` (`java.util.UUID`)

### `requester` (`java.lang.String`)

### `resolvedAt` (`java.time.Instant`)

### `state` (`io.casehub.qhorus.api.message.CommitmentState`)

### `tenancyId` (`java.lang.String`)

## Record Components

### `acknowledgedAt` (`java.time.Instant`)

### `channelId` (`java.util.UUID`)

### `correlationId` (`java.lang.String`)

### `createdAt` (`java.time.Instant`)

### `delegatedTo` (`java.lang.String`)

### `expiresAt` (`java.time.Instant`)

### `id` (`java.util.UUID`)

### `messageType` (`io.casehub.qhorus.api.message.MessageType`)

### `obligor` (`java.lang.String`)

### `parentCommitmentId` (`java.util.UUID`)

### `requester` (`java.lang.String`)

### `resolvedAt` (`java.time.Instant`)

### `state` (`io.casehub.qhorus.api.message.CommitmentState`)

### `tenancyId` (`java.lang.String`)

## Constructors

### `public Commitment(java.util.UUID id, java.lang.String correlationId, java.util.UUID channelId, io.casehub.qhorus.api.message.MessageType messageType, java.lang.String requester, java.lang.String obligor, io.casehub.qhorus.api.message.CommitmentState state, java.time.Instant expiresAt, java.time.Instant acknowledgedAt, java.time.Instant resolvedAt, java.lang.String delegatedTo, java.util.UUID parentCommitmentId, java.lang.String tenancyId, java.time.Instant createdAt)`

#### Parameters

- `id` (`java.util.UUID`)
- `correlationId` (`java.lang.String`)
- `channelId` (`java.util.UUID`)
- `messageType` (`io.casehub.qhorus.api.message.MessageType`)
- `requester` (`java.lang.String`)
- `obligor` (`java.lang.String`)
- `state` (`io.casehub.qhorus.api.message.CommitmentState`)
- `expiresAt` (`java.time.Instant`)
- `acknowledgedAt` (`java.time.Instant`)
- `resolvedAt` (`java.time.Instant`)
- `delegatedTo` (`java.lang.String`)
- `parentCommitmentId` (`java.util.UUID`)
- `tenancyId` (`java.lang.String`)
- `createdAt` (`java.time.Instant`)

## Methods

### `public java.time.Instant acknowledgedAt()`

### `public static io.casehub.qhorus.api.message.Commitment.Builder builder()`

### `public java.util.UUID channelId()`

### `public java.lang.String correlationId()`

### `public java.time.Instant createdAt()`

### `public java.lang.String delegatedTo()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public java.time.Instant expiresAt()`

### `public final int hashCode()`

### `public java.util.UUID id()`

### `public io.casehub.qhorus.api.message.MessageType messageType()`

### `public java.lang.String obligor()`

### `public java.util.UUID parentCommitmentId()`

### `public java.lang.String requester()`

### `public java.time.Instant resolvedAt()`

### `public io.casehub.qhorus.api.message.CommitmentState state()`

### `public java.lang.String tenancyId()`

### `public io.casehub.qhorus.api.message.Commitment.Builder toBuilder()`

### `public final java.lang.String toString()`
