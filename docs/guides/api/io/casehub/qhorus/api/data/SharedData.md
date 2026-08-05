# io.casehub.qhorus.api.data.SharedData

**Package:** `io.casehub.qhorus.api.data`

**Kind:** `record`

## Fields

### `complete` (`boolean`)

### `content` (`java.lang.String`)

### `createdAt` (`java.time.Instant`)

### `createdBy` (`java.lang.String`)

### `description` (`java.lang.String`)

### `id` (`java.util.UUID`)

### `key` (`java.lang.String`)

### `sizeBytes` (`long`)

### `updatedAt` (`java.time.Instant`)

## Record Components

### `complete` (`boolean`)

### `content` (`java.lang.String`)

### `createdAt` (`java.time.Instant`)

### `createdBy` (`java.lang.String`)

### `description` (`java.lang.String`)

### `id` (`java.util.UUID`)

### `key` (`java.lang.String`)

### `sizeBytes` (`long`)

### `updatedAt` (`java.time.Instant`)

## Constructors

### `public SharedData(java.util.UUID id, java.lang.String key, java.lang.String content, java.lang.String createdBy, java.lang.String description, boolean complete, long sizeBytes, java.time.Instant createdAt, java.time.Instant updatedAt)`

#### Parameters

- `id` (`java.util.UUID`)
- `key` (`java.lang.String`)
- `content` (`java.lang.String`)
- `createdBy` (`java.lang.String`)
- `description` (`java.lang.String`)
- `complete` (`boolean`)
- `sizeBytes` (`long`)
- `createdAt` (`java.time.Instant`)
- `updatedAt` (`java.time.Instant`)

## Methods

### `public static io.casehub.qhorus.api.data.SharedData.Builder builder(java.lang.String key)`

#### Parameters

- `key` (`java.lang.String`)

### `public boolean complete()`

### `public java.lang.String content()`

### `public java.time.Instant createdAt()`

### `public java.lang.String createdBy()`

### `public java.lang.String description()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.util.UUID id()`

### `public java.lang.String key()`

### `public long sizeBytes()`

### `public io.casehub.qhorus.api.data.SharedData.Builder toBuilder()`

### `public final java.lang.String toString()`

### `public java.time.Instant updatedAt()`
