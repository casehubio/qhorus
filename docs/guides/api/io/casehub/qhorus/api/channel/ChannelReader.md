# io.casehub.qhorus.api.channel.ChannelReader

**Package:** `io.casehub.qhorus.api.channel`

**Kind:** `interface`

## Methods

### `public abstract java.util.Optional<io.casehub.qhorus.api.channel.Channel> findById(java.util.UUID id)`

#### Parameters

- `id` (`java.util.UUID`)

### `public abstract java.util.List<io.casehub.qhorus.api.channel.Channel> findByIds(java.util.Collection<java.util.UUID> ids)`

#### Parameters

- `ids` (`java.util.Collection<java.util.UUID>`)

### `public abstract java.util.Optional<io.casehub.qhorus.api.channel.Channel> findByName(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public abstract java.util.List<io.casehub.qhorus.api.channel.Channel> findByNamePrefix(java.lang.String prefix)`

#### Parameters

- `prefix` (`java.lang.String`)

### `public abstract java.util.List<io.casehub.qhorus.api.channel.Channel> listAll()`

### `public abstract java.util.List<io.casehub.qhorus.api.channel.Channel> scan(io.casehub.qhorus.api.store.query.ChannelQuery query)`

#### Parameters

- `query` (`io.casehub.qhorus.api.store.query.ChannelQuery`)
