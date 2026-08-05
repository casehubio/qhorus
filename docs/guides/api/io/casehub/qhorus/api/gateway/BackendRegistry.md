# io.casehub.qhorus.api.gateway.BackendRegistry

**Package:** `io.casehub.qhorus.api.gateway`

**Kind:** `interface`

## Methods

### `public abstract void deregisterBackend(java.util.UUID channelId, java.lang.String backendId)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `backendId` (`java.lang.String`)

### `public abstract java.util.List<io.casehub.qhorus.api.gateway.BackendRegistration> listBackends(java.util.UUID channelId)`

#### Parameters

- `channelId` (`java.util.UUID`)

### `public abstract void registerBackend(java.util.UUID channelId, io.casehub.qhorus.api.gateway.ChannelBackend backend, java.lang.String backendType)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `backend` (`io.casehub.qhorus.api.gateway.ChannelBackend`)
- `backendType` (`java.lang.String`)
