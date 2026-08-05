# io.casehub.qhorus.api.store.WatchdogStore

**Package:** `io.casehub.qhorus.api.store`

**Kind:** `interface`

## Methods

### `public abstract void delete(java.util.UUID id)`

#### Parameters

- `id` (`java.util.UUID`)

### `public abstract java.util.Optional<io.casehub.qhorus.api.watchdog.Watchdog> find(java.util.UUID id)`

#### Parameters

- `id` (`java.util.UUID`)

### `public abstract io.casehub.qhorus.api.watchdog.Watchdog put(io.casehub.qhorus.api.watchdog.Watchdog watchdog)`

#### Parameters

- `watchdog` (`io.casehub.qhorus.api.watchdog.Watchdog`)

### `public abstract java.util.List<io.casehub.qhorus.api.watchdog.Watchdog> scan(io.casehub.qhorus.api.store.query.WatchdogQuery query)`

#### Parameters

- `query` (`io.casehub.qhorus.api.store.query.WatchdogQuery`)
