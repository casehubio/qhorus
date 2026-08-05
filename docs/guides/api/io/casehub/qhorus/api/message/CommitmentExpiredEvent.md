# io.casehub.qhorus.api.message.CommitmentExpiredEvent

**Package:** `io.casehub.qhorus.api.message`

**Kind:** `record`

CDI event fired when a commitment transitions to `CommitmentState.EXPIRED`.

<p>Fired once per commitment by `io.casehub.qhorus.runtime.message.CommitmentService.expireOverdue()`.
Consumers observe this as the signal source for deadline-based rerouting and stall-detection
alerts (e.g. engine `OutcomePolicy.onExpired`, devtown investigation alerts).

<p>Refs qhorus#281.

## Fields

### `channelId` (`java.util.UUID`)

### `commitmentId` (`java.util.UUID`)

### `correlationId` (`java.lang.String`)

### `expiresAt` (`java.time.Instant`)

### `obligor` (`java.lang.String`)

### `requester` (`java.lang.String`)

## Record Components

### `channelId` (`java.util.UUID`)

channel on which the commitment was tracked

### `commitmentId` (`java.util.UUID`)

the UUID of the expired commitment

### `correlationId` (`java.lang.String`)

correlationId of the original COMMAND/QUERY

### `expiresAt` (`java.time.Instant`)

the deadline that was missed — useful for computing stall duration

### `obligor` (`java.lang.String`)

the agent that went silent (null for broadcast commitments)

### `requester` (`java.lang.String`)

the original requester (sender of the COMMAND/QUERY)

## Constructors

### `public CommitmentExpiredEvent(java.util.UUID commitmentId, java.lang.String correlationId, java.util.UUID channelId, java.lang.String obligor, java.lang.String requester, java.time.Instant expiresAt)`

#### Parameters

- `commitmentId` (`java.util.UUID`)
- `correlationId` (`java.lang.String`)
- `channelId` (`java.util.UUID`)
- `obligor` (`java.lang.String`)
- `requester` (`java.lang.String`)
- `expiresAt` (`java.time.Instant`)

## Methods

### `public java.util.UUID channelId()`

### `public java.util.UUID commitmentId()`

### `public java.lang.String correlationId()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public java.time.Instant expiresAt()`

### `public final int hashCode()`

### `public java.lang.String obligor()`

### `public java.lang.String requester()`

### `public final java.lang.String toString()`
