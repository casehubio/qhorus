# io.casehub.qhorus.api.store.InstanceStore

**Package:** `io.casehub.qhorus.api.store`

**Kind:** `interface`

## Methods

### `public abstract void delete(java.util.UUID id)`

#### Parameters

- `id` (`java.util.UUID`)

### `public abstract void deleteCapabilities(java.util.UUID instanceId)`

#### Parameters

- `instanceId` (`java.util.UUID`)

### `public abstract java.util.Optional<io.casehub.qhorus.api.instance.Instance> find(java.util.UUID id)`

#### Parameters

- `id` (`java.util.UUID`)

### `public abstract java.util.Optional<io.casehub.qhorus.api.instance.Instance> findByInstanceId(java.lang.String instanceId)`

#### Parameters

- `instanceId` (`java.lang.String`)

### `public abstract java.util.List<java.lang.String> findCapabilities(java.util.UUID instanceId)`

#### Parameters

- `instanceId` (`java.util.UUID`)

### `public abstract io.casehub.qhorus.api.instance.Instance put(io.casehub.qhorus.api.instance.Instance instance)`

#### Parameters

- `instance` (`io.casehub.qhorus.api.instance.Instance`)

### `public abstract void putCapabilities(java.util.UUID instanceId, java.util.List<java.lang.String> tags)`

#### Parameters

- `instanceId` (`java.util.UUID`)
- `tags` (`java.util.List<java.lang.String>`)

### `public abstract java.util.List<io.casehub.qhorus.api.instance.Instance> scan(io.casehub.qhorus.api.store.query.InstanceQuery query)`

#### Parameters

- `query` (`io.casehub.qhorus.api.store.query.InstanceQuery`)
