# io.casehub.qhorus.api.spi.ProjectionResult

**Package:** `io.casehub.qhorus.api.spi`

**Kind:** `record`

The result of a `ChannelProjection` fold: materialised state plus the
ID of the last message folded (`lastMessageId`).

<p>`lastMessageId` is `null` when the channel was empty — no messages
were folded. Use `.isEmpty()` to distinguish this case.

<p>Pass this as `previous` to the incremental `project()` overload
on `ProjectionService` to resume folding from the cursor without re-reading
earlier messages:

<pre>`var result = service.project(channelId, projection);    // full scan
// ... later, after new messages arrive ...
result = service.project(channelId, result, projection); // incremental`</pre>

<p><strong>Contract:</strong> only pass results obtained from
`ProjectionService.project()` — never construct manually. A manually
constructed instance with a non-null `state` and a null `lastMessageId`
has undefined behaviour in the incremental overload: the service treats
`lastMessageId == null` as "channel was empty, start from identity()"
and will silently ignore the provided `state`.

## Fields

### `lastMessageId` (`java.lang.Long`)

### `state` (`S`)

## Record Components

### `lastMessageId` (`java.lang.Long`)

### `state` (`S`)

## Constructors

### `public ProjectionResult(S state, java.lang.Long lastMessageId)`

#### Parameters

- `state` (`S`)
- `lastMessageId` (`java.lang.Long`)

## Methods

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public boolean isEmpty()`

Returns `true` when the channel was empty at the time of the fold —
no messages were processed and `.state()` equals `identity()`.

### `public java.lang.Long lastMessageId()`

### `public S state()`

### `public final java.lang.String toString()`
