# io.casehub.qhorus.api.channel.Presence

**Package:** `io.casehub.qhorus.api.channel`

**Kind:** `record`

## Fields

### `lastSeenAt` (`java.time.Instant`)

### `memberId` (`java.lang.String`)

### `reportedStatus` (`io.casehub.qhorus.api.channel.PresenceStatus`)

### `status` (`io.casehub.qhorus.api.channel.PresenceStatus`)

### `statusMessage` (`java.lang.String`)

## Record Components

### `lastSeenAt` (`java.time.Instant`)

### `memberId` (`java.lang.String`)

### `reportedStatus` (`io.casehub.qhorus.api.channel.PresenceStatus`)

### `status` (`io.casehub.qhorus.api.channel.PresenceStatus`)

### `statusMessage` (`java.lang.String`)

## Constructors

### `public Presence(java.lang.String memberId, io.casehub.qhorus.api.channel.PresenceStatus status, io.casehub.qhorus.api.channel.PresenceStatus reportedStatus, java.time.Instant lastSeenAt, java.lang.String statusMessage)`

#### Parameters

- `memberId` (`java.lang.String`)
- `status` (`io.casehub.qhorus.api.channel.PresenceStatus`)
- `reportedStatus` (`io.casehub.qhorus.api.channel.PresenceStatus`)
- `lastSeenAt` (`java.time.Instant`)
- `statusMessage` (`java.lang.String`)

## Methods

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.time.Instant lastSeenAt()`

### `public java.lang.String memberId()`

### `public io.casehub.qhorus.api.channel.PresenceStatus reportedStatus()`

### `public io.casehub.qhorus.api.channel.PresenceStatus status()`

### `public java.lang.String statusMessage()`

### `public final java.lang.String toString()`
