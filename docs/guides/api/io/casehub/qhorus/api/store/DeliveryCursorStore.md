# io.casehub.qhorus.api.store.DeliveryCursorStore

**Package:** `io.casehub.qhorus.api.store`

**Kind:** `interface`

## Methods

### `public abstract void deleteByChannel(java.util.UUID channelId)`

#### Parameters

- `channelId` (`java.util.UUID`)

### `public abstract java.util.List<io.casehub.qhorus.api.gateway.DeliveryCursor> findAll()`

### `public abstract java.util.List<io.casehub.qhorus.api.gateway.DeliveryCursor> findByChannel(java.util.UUID channelId)`

#### Parameters

- `channelId` (`java.util.UUID`)

### `public abstract java.util.Optional<io.casehub.qhorus.api.gateway.DeliveryCursor> findByChannelAndBackend(java.util.UUID channelId, java.lang.String backendId)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `backendId` (`java.lang.String`)

### `public abstract io.casehub.qhorus.api.gateway.DeliveryCursor save(io.casehub.qhorus.api.gateway.DeliveryCursor cursor)`

#### Parameters

- `cursor` (`io.casehub.qhorus.api.gateway.DeliveryCursor`)
