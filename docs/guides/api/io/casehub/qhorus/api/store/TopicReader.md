# io.casehub.qhorus.api.store.TopicReader

**Package:** `io.casehub.qhorus.api.store`

**Kind:** `interface`

## Methods

### `public abstract java.util.Optional<io.casehub.qhorus.api.message.Topic> find(java.util.UUID channelId, java.lang.String name)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `name` (`java.lang.String`)

### `public abstract java.util.List<io.casehub.qhorus.api.message.Topic> findByChannel(java.util.UUID channelId)`

#### Parameters

- `channelId` (`java.util.UUID`)

### `public abstract java.util.Optional<io.casehub.qhorus.api.message.Topic> findById(java.lang.Long id)`

#### Parameters

- `id` (`java.lang.Long`)
