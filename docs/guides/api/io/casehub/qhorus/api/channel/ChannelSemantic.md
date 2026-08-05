# io.casehub.qhorus.api.channel.ChannelSemantic

**Package:** `io.casehub.qhorus.api.channel`

**Kind:** `enum`

## Enum Constants

### `APPEND` (`io.casehub.qhorus.api.channel.ChannelSemantic`)

Ordered accumulation — default for conversation threads.

### `BARRIER` (`io.casehub.qhorus.api.channel.ChannelSemantic`)

Releases only when all declared contributors have written. Join gate.

### `COLLECT` (`io.casehub.qhorus.api.channel.ChannelSemantic`)

N writers contribute; delivered atomically then cleared. Fan-in primitive.

### `EPHEMERAL` (`io.casehub.qhorus.api.channel.ChannelSemantic`)

Visible to next reader only, then cleared. Routing hints, transient context.

### `LAST_WRITE` (`io.casehub.qhorus.api.channel.ChannelSemantic`)

One authoritative writer; concurrent writes return 409.

## Constructors

### `private ChannelSemantic()`

## Methods

### `public static io.casehub.qhorus.api.channel.ChannelSemantic valueOf(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public static io.casehub.qhorus.api.channel.ChannelSemantic[] values()`
