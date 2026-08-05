# io.casehub.qhorus.api.channel.ChannelManager

**Package:** `io.casehub.qhorus.api.channel`

**Kind:** `interface`

## Methods

### `public abstract io.casehub.qhorus.api.channel.Channel create(io.casehub.qhorus.api.channel.ChannelCreateRequest request)`

#### Parameters

- `request` (`io.casehub.qhorus.api.channel.ChannelCreateRequest`)

### `public abstract long delete(java.util.UUID channelId, boolean force)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `force` (`boolean`)

### `public abstract io.casehub.qhorus.api.channel.FindOrCreateResult findOrCreate(io.casehub.qhorus.api.channel.ChannelCreateRequest request)`

#### Parameters

- `request` (`io.casehub.qhorus.api.channel.ChannelCreateRequest`)

### `public abstract io.casehub.qhorus.api.channel.Channel pause(java.util.UUID channelId)`

#### Parameters

- `channelId` (`java.util.UUID`)

### `public abstract io.casehub.qhorus.api.channel.Channel resume(java.util.UUID channelId)`

#### Parameters

- `channelId` (`java.util.UUID`)

### `public abstract io.casehub.qhorus.api.channel.Channel setAdminInstances(java.util.UUID channelId, java.util.List<java.lang.String> adminInstances)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `adminInstances` (`java.util.List<java.lang.String>`)

### `public abstract io.casehub.qhorus.api.channel.Channel setAllowedWriters(java.util.UUID channelId, java.util.List<java.lang.String> allowedWriters)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `allowedWriters` (`java.util.List<java.lang.String>`)

### `public abstract io.casehub.qhorus.api.channel.Channel setProtocolParticipants(java.util.UUID channelId, java.util.List<java.lang.String> protocolParticipants)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `protocolParticipants` (`java.util.List<java.lang.String>`)

### `public abstract io.casehub.qhorus.api.channel.Channel setProtocols(java.util.UUID channelId, java.util.List<java.lang.String> protocols)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `protocols` (`java.util.List<java.lang.String>`)

### `public abstract io.casehub.qhorus.api.channel.Channel setRateLimits(java.util.UUID channelId, java.lang.Integer perChannel, java.lang.Integer perInstance)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `perChannel` (`java.lang.Integer`)
- `perInstance` (`java.lang.Integer`)

### `public abstract io.casehub.qhorus.api.channel.Channel setReviewerInstances(java.util.UUID channelId, java.util.List<java.lang.String> reviewerInstances)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `reviewerInstances` (`java.util.List<java.lang.String>`)

### `public abstract io.casehub.qhorus.api.channel.Channel setTypeConstraints(java.util.UUID channelId, java.util.Set<io.casehub.qhorus.api.message.MessageType> allowedTypes, java.util.Set<io.casehub.qhorus.api.message.MessageType> deniedTypes)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `allowedTypes` (`java.util.Set<io.casehub.qhorus.api.message.MessageType>`)
- `deniedTypes` (`java.util.Set<io.casehub.qhorus.api.message.MessageType>`)
