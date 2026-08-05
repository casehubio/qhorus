# io.casehub.qhorus.api.channel.ChannelMembership

**Package:** `io.casehub.qhorus.api.channel`

**Kind:** `record`

## Fields

### `channelId` (`java.util.UUID`)

### `id` (`java.lang.Long`)

### `joinedAt` (`java.time.Instant`)

### `lastDeliveredMessageId` (`java.lang.Long`)

### `lastReadMessageId` (`java.lang.Long`)

### `memberId` (`java.lang.String`)

### `role` (`io.casehub.qhorus.api.channel.MemberRole`)

### `tenancyId` (`java.lang.String`)

## Record Components

### `channelId` (`java.util.UUID`)

### `id` (`java.lang.Long`)

### `joinedAt` (`java.time.Instant`)

### `lastDeliveredMessageId` (`java.lang.Long`)

### `lastReadMessageId` (`java.lang.Long`)

### `memberId` (`java.lang.String`)

### `role` (`io.casehub.qhorus.api.channel.MemberRole`)

### `tenancyId` (`java.lang.String`)

## Constructors

### `public ChannelMembership(java.lang.Long id, java.util.UUID channelId, java.lang.String memberId, io.casehub.qhorus.api.channel.MemberRole role, java.lang.String tenancyId, java.time.Instant joinedAt, java.lang.Long lastReadMessageId)`

#### Parameters

- `id` (`java.lang.Long`)
- `channelId` (`java.util.UUID`)
- `memberId` (`java.lang.String`)
- `role` (`io.casehub.qhorus.api.channel.MemberRole`)
- `tenancyId` (`java.lang.String`)
- `joinedAt` (`java.time.Instant`)
- `lastReadMessageId` (`java.lang.Long`)

### `public ChannelMembership(java.lang.Long id, java.util.UUID channelId, java.lang.String memberId, io.casehub.qhorus.api.channel.MemberRole role, java.lang.String tenancyId, java.time.Instant joinedAt, java.lang.Long lastReadMessageId, java.lang.Long lastDeliveredMessageId)`

#### Parameters

- `id` (`java.lang.Long`)
- `channelId` (`java.util.UUID`)
- `memberId` (`java.lang.String`)
- `role` (`io.casehub.qhorus.api.channel.MemberRole`)
- `tenancyId` (`java.lang.String`)
- `joinedAt` (`java.time.Instant`)
- `lastReadMessageId` (`java.lang.Long`)
- `lastDeliveredMessageId` (`java.lang.Long`)

## Methods

### `public java.util.UUID channelId()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.lang.Long id()`

### `public java.time.Instant joinedAt()`

### `public java.lang.Long lastDeliveredMessageId()`

### `public java.lang.Long lastReadMessageId()`

### `public java.lang.String memberId()`

### `public io.casehub.qhorus.api.channel.MemberRole role()`

### `public java.lang.String tenancyId()`

### `public final java.lang.String toString()`
