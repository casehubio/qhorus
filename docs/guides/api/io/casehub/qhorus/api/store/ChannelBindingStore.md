# io.casehub.qhorus.api.store.ChannelBindingStore

**Package:** `io.casehub.qhorus.api.store`

**Kind:** `interface`

## Methods

### `public abstract void delete(java.util.UUID channelId)`

#### Parameters

- `channelId` (`java.util.UUID`)

### `public abstract java.util.Map<java.util.UUID,io.casehub.qhorus.api.channel.ChannelConnectorBinding> findAll()`

Returns a snapshot of all bindings keyed by channelId. Callers must not mutate the map.

### `public abstract java.util.Optional<io.casehub.qhorus.api.channel.ChannelConnectorBinding> findByChannelId(java.util.UUID channelId)`

#### Parameters

- `channelId` (`java.util.UUID`)

### `public abstract java.util.Optional<io.casehub.qhorus.api.channel.ChannelConnectorBinding> findByKey(java.lang.String inboundConnectorId, java.lang.String externalKey)`

#### Parameters

- `inboundConnectorId` (`java.lang.String`)
- `externalKey` (`java.lang.String`)

### `public abstract void put(io.casehub.qhorus.api.channel.ChannelConnectorBinding binding)`

#### Parameters

- `binding` (`io.casehub.qhorus.api.channel.ChannelConnectorBinding`)

### `public default java.util.Optional<io.casehub.qhorus.api.channel.ChannelConnectorBinding> putIfAbsent(io.casehub.qhorus.api.channel.ChannelConnectorBinding binding)`

Atomically creates the binding if no binding exists for the same (inboundConnectorId, externalKey).

#### Parameters

- `binding` (`io.casehub.qhorus.api.channel.ChannelConnectorBinding`)

#### Returns

the existing binding if one was already present; empty if the new binding was created
