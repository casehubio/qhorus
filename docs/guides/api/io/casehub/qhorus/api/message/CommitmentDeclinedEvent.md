# io.casehub.qhorus.api.message.CommitmentDeclinedEvent

**Package:** `io.casehub.qhorus.api.message`

**Kind:** `record`

CDI event fired when a commitment transitions to `CommitmentState.DECLINED`.

<p>Consumers observe this for scope-calibration signals — e.g. trust dimension tracking
("Does the agent correctly DECLINE work outside its capability?").

<p>Refs qhorus#251.

## Fields

### `channelId` (`java.util.UUID`)

### `commitmentId` (`java.util.UUID`)

### `correlationId` (`java.lang.String`)

### `obligor` (`java.lang.String`)

### `requester` (`java.lang.String`)

## Record Components

### `channelId` (`java.util.UUID`)

channel on which the commitment was tracked

### `commitmentId` (`java.util.UUID`)

the UUID of the declined commitment

### `correlationId` (`java.lang.String`)

correlationId of the original COMMAND/QUERY

### `obligor` (`java.lang.String`)

the agent that declined (sender of the DECLINE message)

### `requester` (`java.lang.String`)

the original requester (sender of the COMMAND/QUERY)

## Constructors

### `public CommitmentDeclinedEvent(java.util.UUID commitmentId, java.lang.String correlationId, java.util.UUID channelId, java.lang.String obligor, java.lang.String requester)`

#### Parameters

- `commitmentId` (`java.util.UUID`)
- `correlationId` (`java.lang.String`)
- `channelId` (`java.util.UUID`)
- `obligor` (`java.lang.String`)
- `requester` (`java.lang.String`)

## Methods

### `public java.util.UUID channelId()`

### `public java.util.UUID commitmentId()`

### `public java.lang.String correlationId()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.lang.String obligor()`

### `public java.lang.String requester()`

### `public final java.lang.String toString()`
