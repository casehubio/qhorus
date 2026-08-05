# io.casehub.qhorus.api.store.ChannelMembershipStore

**Package:** `io.casehub.qhorus.api.store`

**Kind:** `interface`

## Methods

### `public abstract void advanceDeliveredCursorForMembers(java.util.UUID channelId, java.util.Set<java.lang.String> memberIds, java.lang.Long messageId)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `memberIds` (`java.util.Set<java.lang.String>`)
- `messageId` (`java.lang.Long`)

### `public abstract boolean delete(java.util.UUID channelId, java.lang.String memberId)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `memberId` (`java.lang.String`)

### `public abstract void deleteAll(java.util.UUID channelId)`

#### Parameters

- `channelId` (`java.util.UUID`)

### `public abstract io.casehub.qhorus.api.channel.ChannelMembership put(io.casehub.qhorus.api.channel.ChannelMembership membership)`

#### Parameters

- `membership` (`io.casehub.qhorus.api.channel.ChannelMembership`)

### `public abstract void updateLastDeliveredMessageId(java.util.UUID channelId, java.lang.String memberId, java.lang.Long messageId)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `memberId` (`java.lang.String`)
- `messageId` (`java.lang.Long`)

### `public abstract void updateLastReadMessageId(java.util.UUID channelId, java.lang.String memberId, java.lang.Long messageId)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `memberId` (`java.lang.String`)
- `messageId` (`java.lang.Long`)

### `public abstract void updateRole(java.util.UUID channelId, java.lang.String memberId, io.casehub.qhorus.api.channel.MemberRole role)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `memberId` (`java.lang.String`)
- `role` (`io.casehub.qhorus.api.channel.MemberRole`)
