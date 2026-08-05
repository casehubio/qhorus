# io.casehub.qhorus.api.store.CrossTenantMessageStore

**Package:** `io.casehub.qhorus.api.store`

**Kind:** `interface`

Read-only cross-tenant view of all messages across all tenancies.

<p>Obtain via CDI injection:
<pre>`@Inject CrossTenantMessageStore store;`</pre>

<p>Refs #260.

## Methods

### `public abstract long count(io.casehub.qhorus.api.store.query.MessageQuery query)`

Count messages across all tenancies matching the given query.
Used by WatchdogEvaluationService.evaluateQueueDepth().

#### Parameters

- `query` (`io.casehub.qhorus.api.store.query.MessageQuery`)

### `public abstract int countByChannel(java.util.UUID channelId)`

Count messages in the given channel, regardless of tenancy.

#### Parameters

- `channelId` (`java.util.UUID`)

### `public abstract java.util.List<java.lang.String> distinctSendersByChannel(java.util.UUID channelId, io.casehub.qhorus.api.message.MessageType excludedType)`

Distinct sender IDs that have posted to `channelId`,
excluding messages of `excludedType`.

#### Parameters

- `channelId` (`java.util.UUID`) — the channel to query
- `excludedType` (`io.casehub.qhorus.api.message.MessageType`) — message type to exclude from the sender scan
                    (e.g. `MessageType.EVENT` to skip telemetry senders)

### `public abstract java.util.Optional<io.casehub.qhorus.api.message.Message> find(java.lang.Long id)`

Find a message by its primary key, regardless of tenancy.

#### Parameters

- `id` (`java.lang.Long`)

### `public abstract java.util.Optional<io.casehub.qhorus.api.message.Message> findLastMessage(java.util.UUID channelId)`

The most recent message in `channelId` by insertion order,
or empty if the channel has no messages.

#### Parameters

- `channelId` (`java.util.UUID`)

### `public abstract java.util.List<io.casehub.qhorus.api.message.Message> scan(io.casehub.qhorus.api.store.query.MessageQuery query)`

Scan messages across all tenancies matching the given query.
Callers should scope queries with explicit channelId or other
filters to avoid unbounded result sets.

#### Parameters

- `query` (`io.casehub.qhorus.api.store.query.MessageQuery`)
