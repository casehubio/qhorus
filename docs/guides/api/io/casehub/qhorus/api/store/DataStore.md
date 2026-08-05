# io.casehub.qhorus.api.store.DataStore

**Package:** `io.casehub.qhorus.api.store`

**Kind:** `interface`

## Methods

### `public abstract int countClaims(java.util.UUID artefactId)`

#### Parameters

- `artefactId` (`java.util.UUID`)

### `public abstract void delete(java.util.UUID id)`

#### Parameters

- `id` (`java.util.UUID`)

### `public abstract void deleteClaim(java.util.UUID artefactId, java.util.UUID instanceId)`

#### Parameters

- `artefactId` (`java.util.UUID`)
- `instanceId` (`java.util.UUID`)

### `public abstract java.util.Optional<io.casehub.qhorus.api.data.SharedData> find(java.util.UUID id)`

#### Parameters

- `id` (`java.util.UUID`)

### `public abstract java.util.List<io.casehub.qhorus.api.data.SharedData> findByIds(java.util.Collection<java.util.UUID> ids)`

#### Parameters

- `ids` (`java.util.Collection<java.util.UUID>`)

### `public abstract java.util.Optional<io.casehub.qhorus.api.data.SharedData> findByKey(java.lang.String key)`

#### Parameters

- `key` (`java.lang.String`)

### `public abstract io.casehub.qhorus.api.data.SharedData put(io.casehub.qhorus.api.data.SharedData data)`

#### Parameters

- `data` (`io.casehub.qhorus.api.data.SharedData`)

### `public abstract io.casehub.qhorus.api.data.ArtefactClaim putClaim(io.casehub.qhorus.api.data.ArtefactClaim claim)`

#### Parameters

- `claim` (`io.casehub.qhorus.api.data.ArtefactClaim`)

### `public abstract java.util.List<io.casehub.qhorus.api.data.SharedData> scan(io.casehub.qhorus.api.store.query.DataQuery query)`

#### Parameters

- `query` (`io.casehub.qhorus.api.store.query.DataQuery`)
