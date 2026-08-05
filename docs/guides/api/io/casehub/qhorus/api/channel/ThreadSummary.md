# io.casehub.qhorus.api.channel.ThreadSummary

**Package:** `io.casehub.qhorus.api.channel`

**Kind:** `record`

## Fields

### `annotations` (`java.util.Map<java.lang.String,java.lang.String>`)

### `channelId` (`java.util.UUID`)

### `content` (`java.lang.String`)

### `correlationId` (`java.lang.String`)

### `id` (`java.util.UUID`)

### `tenancyId` (`java.lang.String`)

### `updatedAt` (`java.time.Instant`)

### `updatedBy` (`java.lang.String`)

## Record Components

### `annotations` (`java.util.Map<java.lang.String,java.lang.String>`)

### `channelId` (`java.util.UUID`)

### `content` (`java.lang.String`)

### `correlationId` (`java.lang.String`)

### `id` (`java.util.UUID`)

### `tenancyId` (`java.lang.String`)

### `updatedAt` (`java.time.Instant`)

### `updatedBy` (`java.lang.String`)

## Constructors

### `public ThreadSummary(java.util.UUID id, java.util.UUID channelId, java.lang.String correlationId, java.lang.String content, java.util.Map<java.lang.String,java.lang.String> annotations, java.time.Instant updatedAt, java.lang.String updatedBy, java.lang.String tenancyId)`

#### Parameters

- `id` (`java.util.UUID`)
- `channelId` (`java.util.UUID`)
- `correlationId` (`java.lang.String`)
- `content` (`java.lang.String`)
- `annotations` (`java.util.Map<java.lang.String,java.lang.String>`)
- `updatedAt` (`java.time.Instant`)
- `updatedBy` (`java.lang.String`)
- `tenancyId` (`java.lang.String`)

## Methods

### `public java.util.Map<java.lang.String,java.lang.String> annotations()`

### `public static io.casehub.qhorus.api.channel.ThreadSummary.Builder builder(java.util.UUID channelId, java.lang.String correlationId)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `correlationId` (`java.lang.String`)

### `public java.util.UUID channelId()`

### `public java.lang.String content()`

### `public java.lang.String correlationId()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.util.UUID id()`

### `public java.lang.String tenancyId()`

### `public io.casehub.qhorus.api.channel.ThreadSummary.Builder toBuilder()`

### `public final java.lang.String toString()`

### `public java.time.Instant updatedAt()`

### `public java.lang.String updatedBy()`
