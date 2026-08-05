# io.casehub.qhorus.api.gateway.DeliveryCursor

**Package:** `io.casehub.qhorus.api.gateway`

**Kind:** `record`

## Fields

### `backendId` (`java.lang.String`)

### `channelId` (`java.util.UUID`)

### `createdAt` (`java.time.Instant`)

### `id` (`java.util.UUID`)

### `lastDeliveredId` (`java.lang.Long`)

### `lastDeliveredVersion` (`int`)

### `updatedAt` (`java.time.Instant`)

## Record Components

### `backendId` (`java.lang.String`)

### `channelId` (`java.util.UUID`)

### `createdAt` (`java.time.Instant`)

### `id` (`java.util.UUID`)

### `lastDeliveredId` (`java.lang.Long`)

### `lastDeliveredVersion` (`int`)

### `updatedAt` (`java.time.Instant`)

## Constructors

### `public DeliveryCursor(java.util.UUID id, java.util.UUID channelId, java.lang.String backendId, java.lang.Long lastDeliveredId, int lastDeliveredVersion, java.time.Instant updatedAt, java.time.Instant createdAt)`

#### Parameters

- `id` (`java.util.UUID`)
- `channelId` (`java.util.UUID`)
- `backendId` (`java.lang.String`)
- `lastDeliveredId` (`java.lang.Long`)
- `lastDeliveredVersion` (`int`)
- `updatedAt` (`java.time.Instant`)
- `createdAt` (`java.time.Instant`)

## Methods

### `public java.lang.String backendId()`

### `public static io.casehub.qhorus.api.gateway.DeliveryCursor.Builder builder()`

### `public java.util.UUID channelId()`

### `public java.time.Instant createdAt()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.util.UUID id()`

### `public java.lang.Long lastDeliveredId()`

### `public int lastDeliveredVersion()`

### `public io.casehub.qhorus.api.gateway.DeliveryCursor.Builder toBuilder()`

### `public final java.lang.String toString()`

### `public java.time.Instant updatedAt()`
