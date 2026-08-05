# io.casehub.qhorus.api.store.MessageStore

**Package:** `io.casehub.qhorus.api.store`

**Kind:** `interface`

## Methods

### `public abstract void delete(java.lang.Long id)`

#### Parameters

- `id` (`java.lang.Long`)

### `public abstract void deleteAll(java.util.UUID channelId)`

#### Parameters

- `channelId` (`java.util.UUID`)

### `public abstract void deleteNonEvent(java.util.UUID channelId)`

#### Parameters

- `channelId` (`java.util.UUID`)

### `public abstract io.casehub.qhorus.api.message.Message put(io.casehub.qhorus.api.message.Message message)`

#### Parameters

- `message` (`io.casehub.qhorus.api.message.Message`)

### `public abstract int updateChannelId(java.util.UUID sourceChannelId, java.lang.String topic, java.util.UUID targetChannelId)`

#### Parameters

- `sourceChannelId` (`java.util.UUID`)
- `topic` (`java.lang.String`)
- `targetChannelId` (`java.util.UUID`)

### `public abstract int updateTopicName(java.util.UUID channelId, java.lang.String oldTopic, java.lang.String newTopic)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `oldTopic` (`java.lang.String`)
- `newTopic` (`java.lang.String`)
