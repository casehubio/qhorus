# io.casehub.qhorus.api.store.SpaceStore

**Package:** `io.casehub.qhorus.api.store`

**Kind:** `interface`

## Methods

### `public abstract void delete(java.util.UUID id)`

#### Parameters

- `id` (`java.util.UUID`)

### `public abstract java.util.Optional<io.casehub.qhorus.api.channel.Space> find(java.util.UUID id)`

#### Parameters

- `id` (`java.util.UUID`)

### `public default java.util.List<io.casehub.qhorus.api.channel.Space> findByIds(java.util.Collection<java.util.UUID> ids)`

#### Parameters

- `ids` (`java.util.Collection<java.util.UUID>`)

### `public abstract java.util.List<io.casehub.qhorus.api.channel.Space> findByName(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public abstract boolean hasChildren(java.util.UUID spaceId)`

#### Parameters

- `spaceId` (`java.util.UUID`)

### `public abstract java.util.List<io.casehub.qhorus.api.channel.Space> listByParent(java.util.UUID parentSpaceId)`

#### Parameters

- `parentSpaceId` (`java.util.UUID`)

### `public abstract java.util.List<io.casehub.qhorus.api.channel.Space> listRoots()`

### `public abstract io.casehub.qhorus.api.channel.Space put(io.casehub.qhorus.api.channel.Space space)`

#### Parameters

- `space` (`io.casehub.qhorus.api.channel.Space`)
