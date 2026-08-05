# io.casehub.qhorus.api.store.TopicStore

**Package:** `io.casehub.qhorus.api.store`

**Kind:** `interface`

## Methods

### `public abstract void delete(java.util.UUID channelId, java.lang.String name)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `name` (`java.lang.String`)

### `public abstract void deleteAll(java.util.UUID channelId)`

#### Parameters

- `channelId` (`java.util.UUID`)

### `public abstract io.casehub.qhorus.api.message.Topic put(io.casehub.qhorus.api.message.Topic topic)`

#### Parameters

- `topic` (`io.casehub.qhorus.api.message.Topic`)

### `public abstract int rename(java.util.UUID channelId, java.lang.String oldName, java.lang.String newName)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `oldName` (`java.lang.String`)
- `newName` (`java.lang.String`)
