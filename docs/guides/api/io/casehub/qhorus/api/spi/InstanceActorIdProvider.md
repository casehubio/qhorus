# io.casehub.qhorus.api.spi.InstanceActorIdProvider

**Package:** `io.casehub.qhorus.api.spi`

**Kind:** `interface`

Maps a Qhorus `instanceId` (session-scoped, e.g. `claudony-worker-abc123`) to
a ledger `actorId` (persona-scoped, e.g. `claude:analyst@v1`).

<p>
Default implementation is a no-op identity function.
Replace with `@Alternative @Priority` to provide session-to-persona mapping.

<p>
Refs #124.

## Methods

### `public abstract java.lang.String resolve(java.lang.String instanceId)`

Resolve a Qhorus instanceId to a ledger actorId.
Return the instanceId unchanged if no mapping is known. Never return null.

#### Parameters

- `instanceId` (`java.lang.String`) — the Qhorus instance identifier (e.g. `message.sender`)

#### Returns

the ledger actorId to use; never null
