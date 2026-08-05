# io.casehub.qhorus.api.store.CommitmentStore

**Package:** `io.casehub.qhorus.api.store`

**Kind:** `interface`

## Methods

### `public abstract long deleteAll(java.util.UUID channelId)`

Delete all commitments for the given channel. Called by delete_channel before channel deletion.

#### Parameters

- `channelId` (`java.util.UUID`)

### `public abstract void deleteById(java.util.UUID commitmentId)`

#### Parameters

- `commitmentId` (`java.util.UUID`)

### `public abstract long deleteExpiredBefore(java.time.Instant cutoff)`

#### Parameters

- `cutoff` (`java.time.Instant`)

### `public abstract io.casehub.qhorus.api.message.Commitment save(io.casehub.qhorus.api.message.Commitment commitment)`

#### Parameters

- `commitment` (`io.casehub.qhorus.api.message.Commitment`)
