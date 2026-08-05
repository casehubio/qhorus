# io.casehub.qhorus.api.spi.ObligorTrustContext

**Package:** `io.casehub.qhorus.api.spi`

**Kind:** `record`

Context passed to `ObligorTrustPolicy.permits` when a COMMAND message targets
a named obligor.

<p>`channelId` is the stable key; `channelName` is the human-readable name
used by custom implementations that map channel names to trust dimensions (e.g. Claudony's
capability-scoped evaluation).

<p>Refs #213.

## Fields

### `channelId` (`java.util.UUID`)

### `channelName` (`java.lang.String`)

### `obligorId` (`java.lang.String`)

## Record Components

### `channelId` (`java.util.UUID`)

### `channelName` (`java.lang.String`)

### `obligorId` (`java.lang.String`)

## Constructors

### `public ObligorTrustContext(java.lang.String obligorId, java.util.UUID channelId, java.lang.String channelName)`

#### Parameters

- `obligorId` (`java.lang.String`)
- `channelId` (`java.util.UUID`)
- `channelName` (`java.lang.String`)

## Methods

### `public java.util.UUID channelId()`

### `public java.lang.String channelName()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.lang.String obligorId()`

### `public final java.lang.String toString()`
