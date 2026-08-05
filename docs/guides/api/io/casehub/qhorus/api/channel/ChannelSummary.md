# io.casehub.qhorus.api.channel.ChannelSummary

**Package:** `io.casehub.qhorus.api.channel`

**Kind:** `record`

## Fields

### `annotations` (`java.util.Map<java.lang.String,java.lang.String>`)

### `channelId` (`java.util.UUID`)

### `content` (`java.lang.String`)

### `id` (`java.util.UUID`)

### `lastUpdatedMessageId` (`java.lang.Long`)

### `tenancyId` (`java.lang.String`)

### `updateAfterMessages` (`java.lang.Integer`)

### `updateAfterSeconds` (`java.lang.Integer`)

### `updatedAt` (`java.time.Instant`)

### `updatedBy` (`java.lang.String`)

## Record Components

### `annotations` (`java.util.Map<java.lang.String,java.lang.String>`)

### `channelId` (`java.util.UUID`)

### `content` (`java.lang.String`)

### `id` (`java.util.UUID`)

### `lastUpdatedMessageId` (`java.lang.Long`)

### `tenancyId` (`java.lang.String`)

### `updateAfterMessages` (`java.lang.Integer`)

### `updateAfterSeconds` (`java.lang.Integer`)

### `updatedAt` (`java.time.Instant`)

### `updatedBy` (`java.lang.String`)

## Constructors

### `public ChannelSummary(java.util.UUID id, java.util.UUID channelId, java.lang.String content, java.util.Map<java.lang.String,java.lang.String> annotations, java.time.Instant updatedAt, java.lang.String updatedBy, java.lang.Long lastUpdatedMessageId, java.lang.Integer updateAfterMessages, java.lang.Integer updateAfterSeconds, java.lang.String tenancyId)`

#### Parameters

- `id` (`java.util.UUID`)
- `channelId` (`java.util.UUID`)
- `content` (`java.lang.String`)
- `annotations` (`java.util.Map<java.lang.String,java.lang.String>`)
- `updatedAt` (`java.time.Instant`)
- `updatedBy` (`java.lang.String`)
- `lastUpdatedMessageId` (`java.lang.Long`)
- `updateAfterMessages` (`java.lang.Integer`)
- `updateAfterSeconds` (`java.lang.Integer`)
- `tenancyId` (`java.lang.String`)

## Methods

### `public java.util.Map<java.lang.String,java.lang.String> annotations()`

### `public static io.casehub.qhorus.api.channel.ChannelSummary.Builder builder(java.util.UUID channelId)`

#### Parameters

- `channelId` (`java.util.UUID`)

### `public java.util.UUID channelId()`

### `public java.lang.String content()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.util.UUID id()`

### `public java.lang.Long lastUpdatedMessageId()`

### `public java.lang.String tenancyId()`

### `public io.casehub.qhorus.api.channel.ChannelSummary.Builder toBuilder()`

### `public final java.lang.String toString()`

### `public java.lang.Integer updateAfterMessages()`

### `public java.lang.Integer updateAfterSeconds()`

### `public java.time.Instant updatedAt()`

### `public java.lang.String updatedBy()`
