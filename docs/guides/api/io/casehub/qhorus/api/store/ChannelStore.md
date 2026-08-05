# io.casehub.qhorus.api.store.ChannelStore

**Package:** `io.casehub.qhorus.api.store`

**Kind:** `interface`

## Methods

### `public abstract void delete(java.util.UUID id)`

#### Parameters

- `id` (`java.util.UUID`)

### `public abstract java.util.Optional<io.casehub.qhorus.api.channel.Channel> find(java.util.UUID id)`

#### Parameters

- `id` (`java.util.UUID`)

### `public default java.util.List<io.casehub.qhorus.api.channel.Channel> findByIds(java.util.Collection<java.util.UUID> ids)`

#### Parameters

- `ids` (`java.util.Collection<java.util.UUID>`)

### `public abstract java.util.Optional<io.casehub.qhorus.api.channel.Channel> findByName(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public default boolean hasChannelsInSpace(java.util.UUID spaceId)`

#### Parameters

- `spaceId` (`java.util.UUID`)

### `public abstract io.casehub.qhorus.api.channel.Channel put(io.casehub.qhorus.api.channel.Channel channel)`

#### Parameters

- `channel` (`io.casehub.qhorus.api.channel.Channel`)

### `public abstract java.util.List<io.casehub.qhorus.api.channel.Channel> scan(io.casehub.qhorus.api.store.query.ChannelQuery query)`

#### Parameters

- `query` (`io.casehub.qhorus.api.store.query.ChannelQuery`)

### `public abstract void updateLastActivity(java.util.UUID channelId, java.lang.String tenancyId)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `tenancyId` (`java.lang.String`)

### `public abstract void updateTrackDelivery(java.util.UUID channelId, java.lang.Boolean trackDelivery)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `trackDelivery` (`java.lang.Boolean`)
