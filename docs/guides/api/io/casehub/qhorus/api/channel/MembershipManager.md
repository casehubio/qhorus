# io.casehub.qhorus.api.channel.MembershipManager

**Package:** `io.casehub.qhorus.api.channel`

**Kind:** `interface`

## Methods

### `public abstract io.casehub.qhorus.api.channel.ChannelMembership join(java.util.UUID channelId, java.lang.String memberId)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `memberId` (`java.lang.String`)

### `public abstract void leave(java.util.UUID channelId, java.lang.String memberId)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `memberId` (`java.lang.String`)

### `public abstract void setRole(java.util.UUID channelId, java.lang.String memberId, io.casehub.qhorus.api.channel.MemberRole role)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `memberId` (`java.lang.String`)
- `role` (`io.casehub.qhorus.api.channel.MemberRole`)

### `public abstract void updateLastReadMessageId(java.util.UUID channelId, java.lang.String memberId, java.lang.Long messageId)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `memberId` (`java.lang.String`)
- `messageId` (`java.lang.Long`)
