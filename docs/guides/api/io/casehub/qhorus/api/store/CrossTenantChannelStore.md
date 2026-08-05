# io.casehub.qhorus.api.store.CrossTenantChannelStore

**Package:** `io.casehub.qhorus.api.store`

**Kind:** `interface`

Read-only cross-tenant view of all channels across all tenancies.

<p>Obtain via CDI injection:
<pre>`@Inject CrossTenantChannelStore store;`</pre>

<p>Refs #260.

## Methods

### `public abstract java.util.Optional<io.casehub.qhorus.api.channel.Channel> findById(java.util.UUID id)`

Find a channel by its UUID, regardless of tenancy.

#### Parameters

- `id` (`java.util.UUID`)

### `public abstract java.util.Optional<io.casehub.qhorus.api.channel.Channel> findByNameAndTenancy(java.lang.String name, java.lang.String tenancyId)`

Find a channel by name within a specific tenancy.
Used by WatchdogEvaluationService to locate notification channels
when evaluating conditions that span tenant boundaries.

#### Parameters

- `name` (`java.lang.String`) — the channel slug name
- `tenancyId` (`java.lang.String`) — the tenancy scope to search within

#### Returns

the matching channel, or empty if not found

### `public abstract java.util.List<io.casehub.qhorus.api.channel.Channel> listAll()`

All channels across all tenancies.
