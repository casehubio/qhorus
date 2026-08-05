# io.casehub.qhorus.api.gateway.CommitmentStateChangedEvent

**Package:** `io.casehub.qhorus.api.gateway`

**Kind:** `record`

## Fields

### `channelId` (`java.util.UUID`)

### `commitment` (`io.casehub.qhorus.api.message.Commitment`)

### `previousState` (`io.casehub.qhorus.api.message.CommitmentState`)

## Record Components

### `channelId` (`java.util.UUID`)

### `commitment` (`io.casehub.qhorus.api.message.Commitment`)

### `previousState` (`io.casehub.qhorus.api.message.CommitmentState`)

## Constructors

### `public CommitmentStateChangedEvent(java.util.UUID channelId, io.casehub.qhorus.api.message.Commitment commitment, io.casehub.qhorus.api.message.CommitmentState previousState)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `commitment` (`io.casehub.qhorus.api.message.Commitment`)
- `previousState` (`io.casehub.qhorus.api.message.CommitmentState`)

## Methods

### `public java.util.UUID channelId()`

### `public io.casehub.qhorus.api.message.Commitment commitment()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public io.casehub.qhorus.api.message.CommitmentState previousState()`

### `public final java.lang.String toString()`
