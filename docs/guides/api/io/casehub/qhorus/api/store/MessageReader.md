# io.casehub.qhorus.api.store.MessageReader

**Package:** `io.casehub.qhorus.api.store`

**Kind:** `interface`

## Methods

### `public abstract long count(io.casehub.qhorus.api.store.query.MessageQuery query)`

#### Parameters

- `query` (`io.casehub.qhorus.api.store.query.MessageQuery`)

### `public abstract java.util.Map<java.util.UUID,java.lang.Long> countAllByChannel()`

### `public abstract int countByChannel(java.util.UUID channelId)`

#### Parameters

- `channelId` (`java.util.UUID`)

### `public abstract java.util.List<java.lang.String> distinctSendersByChannel(java.util.UUID channelId, io.casehub.qhorus.api.message.MessageType excludedType)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `excludedType` (`io.casehub.qhorus.api.message.MessageType`)

### `public abstract java.util.Optional<io.casehub.qhorus.api.message.Message> find(java.lang.Long id)`

#### Parameters

- `id` (`java.lang.Long`)

### `public abstract java.util.Optional<io.casehub.qhorus.api.message.Message> findLastMessage(java.util.UUID channelId)`

#### Parameters

- `channelId` (`java.util.UUID`)

### `public abstract java.util.List<io.casehub.qhorus.api.message.MessageView> findRecent(java.util.UUID channelId, int limit)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `limit` (`int`)

### `public abstract java.util.List<io.casehub.qhorus.api.message.Message> scan(io.casehub.qhorus.api.store.query.MessageQuery query)`

#### Parameters

- `query` (`io.casehub.qhorus.api.store.query.MessageQuery`)
