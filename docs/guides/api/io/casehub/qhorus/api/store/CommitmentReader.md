# io.casehub.qhorus.api.store.CommitmentReader

**Package:** `io.casehub.qhorus.api.store`

**Kind:** `interface`

## Methods

### `public abstract java.util.List<io.casehub.qhorus.api.message.Commitment> findAllByCorrelationId(java.lang.String correlationId)`

#### Parameters

- `correlationId` (`java.lang.String`)

### `public abstract java.util.List<io.casehub.qhorus.api.message.Commitment> findAllOpen()`

### `public abstract java.util.List<io.casehub.qhorus.api.message.Commitment> findByChannel(java.util.UUID channelId)`

#### Parameters

- `channelId` (`java.util.UUID`)

### `public abstract java.util.Optional<io.casehub.qhorus.api.message.Commitment> findByCorrelationId(java.lang.String correlationId)`

#### Parameters

- `correlationId` (`java.lang.String`)

### `public abstract java.util.Optional<io.casehub.qhorus.api.message.Commitment> findById(java.util.UUID commitmentId)`

#### Parameters

- `commitmentId` (`java.util.UUID`)

### `public abstract java.util.List<io.casehub.qhorus.api.message.Commitment> findByIds(java.util.Collection<java.util.UUID> ids)`

#### Parameters

- `ids` (`java.util.Collection<java.util.UUID>`)

### `public abstract java.util.List<io.casehub.qhorus.api.message.Commitment> findByState(io.casehub.qhorus.api.message.CommitmentState state, java.util.UUID channelId)`

#### Parameters

- `state` (`io.casehub.qhorus.api.message.CommitmentState`)
- `channelId` (`java.util.UUID`)

### `public abstract java.util.List<io.casehub.qhorus.api.message.Commitment> findExpiredBefore(java.time.Instant cutoff)`

#### Parameters

- `cutoff` (`java.time.Instant`)

### `public abstract java.util.List<io.casehub.qhorus.api.message.Commitment> findOpenByChannelId(java.util.UUID channelId)`

#### Parameters

- `channelId` (`java.util.UUID`)

### `public default java.util.List<io.casehub.qhorus.api.message.Commitment> findOpenByObligor(java.lang.String obligor)`

#### Parameters

- `obligor` (`java.lang.String`)

### `public abstract java.util.List<io.casehub.qhorus.api.message.Commitment> findOpenByObligor(java.lang.String obligor, java.util.UUID channelId)`

#### Parameters

- `obligor` (`java.lang.String`)
- `channelId` (`java.util.UUID`)

### `public abstract java.util.List<io.casehub.qhorus.api.message.Commitment> findOpenByRequester(java.lang.String requester, java.util.UUID channelId)`

#### Parameters

- `requester` (`java.lang.String`)
- `channelId` (`java.util.UUID`)
