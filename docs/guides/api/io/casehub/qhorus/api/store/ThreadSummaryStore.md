# io.casehub.qhorus.api.store.ThreadSummaryStore

**Package:** `io.casehub.qhorus.api.store`

**Kind:** `interface`

## Methods

### `public abstract void delete(java.util.UUID channelId, java.lang.String correlationId)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `correlationId` (`java.lang.String`)

### `public abstract java.util.List<io.casehub.qhorus.api.channel.ThreadSummary> findByChannel(java.util.UUID channelId)`

#### Parameters

- `channelId` (`java.util.UUID`)

### `public abstract java.util.Optional<io.casehub.qhorus.api.channel.ThreadSummary> findByCorrelationId(java.util.UUID channelId, java.lang.String correlationId)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `correlationId` (`java.lang.String`)

### `public abstract io.casehub.qhorus.api.channel.ThreadSummary save(io.casehub.qhorus.api.channel.ThreadSummary summary)`

#### Parameters

- `summary` (`io.casehub.qhorus.api.channel.ThreadSummary`)
