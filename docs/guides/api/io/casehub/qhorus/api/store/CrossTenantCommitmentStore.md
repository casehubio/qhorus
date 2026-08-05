# io.casehub.qhorus.api.store.CrossTenantCommitmentStore

**Package:** `io.casehub.qhorus.api.store`

**Kind:** `interface`

Cross-tenant view of commitments, used for platform-wide obligation management.

<p>Obtain via CDI injection:
<pre>`@Inject CrossTenantCommitmentStore store;`</pre>

<p>Refs #260.

## Methods

### `public abstract void expireOverdue(java.time.Instant cutoff)`

Expire overdue commitments across all tenancies whose `expiresAt`
is strictly before `cutoff`.

<p>Implementations should transition matching OPEN/ACKNOWLEDGED commitments
to EXPIRED state and record the transition timestamp.

#### Parameters

- `cutoff` (`java.time.Instant`) — the expiry boundary; commitments with expiresAt before this are expired

### `public abstract java.util.List<io.casehub.qhorus.api.message.Commitment> findAllByCorrelationId(java.lang.String correlationId)`

All commitments sharing a correlationId (any tenancy), ordered by createdAt ASC.

#### Parameters

- `correlationId` (`java.lang.String`)

### `public abstract java.util.List<io.casehub.qhorus.api.message.Commitment> findAllOpen()`

All OPEN or ACKNOWLEDGED commitments across every tenancy, sorted oldest first.

### `public abstract java.util.List<io.casehub.qhorus.api.message.Commitment> findOpenByChannel(java.util.UUID channelId)`

All non-terminal commitments in the given channel, regardless of tenancy.

#### Parameters

- `channelId` (`java.util.UUID`)
