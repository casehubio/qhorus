# io.casehub.qhorus.api.store.MembershipReader

**Package:** `io.casehub.qhorus.api.store`

**Kind:** `interface`

## Methods

### `public abstract java.util.Optional<io.casehub.qhorus.api.channel.ChannelMembership> find(java.util.UUID channelId, java.lang.String memberId)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `memberId` (`java.lang.String`)

### `public abstract java.util.List<io.casehub.qhorus.api.channel.ChannelMembership> findByChannel(java.util.UUID channelId)`

#### Parameters

- `channelId` (`java.util.UUID`)

### `public abstract java.util.List<io.casehub.qhorus.api.channel.ChannelMembership> findByMember(java.lang.String memberId, java.lang.String tenancyId)`

#### Parameters

- `memberId` (`java.lang.String`)
- `tenancyId` (`java.lang.String`)
