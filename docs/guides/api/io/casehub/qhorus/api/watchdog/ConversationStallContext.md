# io.casehub.qhorus.api.watchdog.ConversationStallContext

**Package:** `io.casehub.qhorus.api.watchdog`

**Kind:** `record`

## Fields

### `channelId` (`java.util.UUID`)

### `channelName` (`java.lang.String`)

### `correlationIds` (`java.util.List<java.lang.String>`)

### `deliveryConfirmed` (`java.lang.Boolean`)

### `stalledCount` (`int`)

### `stalledSeconds` (`long`)

## Record Components

### `channelId` (`java.util.UUID`)

### `channelName` (`java.lang.String`)

### `correlationIds` (`java.util.List<java.lang.String>`)

### `deliveryConfirmed` (`java.lang.Boolean`)

### `stalledCount` (`int`)

### `stalledSeconds` (`long`)

## Constructors

### `public ConversationStallContext(java.util.UUID channelId, java.lang.String channelName, int stalledCount, java.util.List<java.lang.String> correlationIds, long stalledSeconds)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `channelName` (`java.lang.String`)
- `stalledCount` (`int`)
- `correlationIds` (`java.util.List<java.lang.String>`)
- `stalledSeconds` (`long`)

### `public ConversationStallContext(java.util.UUID channelId, java.lang.String channelName, int stalledCount, java.util.List<java.lang.String> correlationIds, long stalledSeconds, java.lang.Boolean deliveryConfirmed)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `channelName` (`java.lang.String`)
- `stalledCount` (`int`)
- `correlationIds` (`java.util.List<java.lang.String>`)
- `stalledSeconds` (`long`)
- `deliveryConfirmed` (`java.lang.Boolean`)

## Methods

### `public java.util.UUID channelId()`

### `public java.lang.String channelName()`

### `public io.casehub.qhorus.api.watchdog.WatchdogConditionType conditionType()`

### `public java.util.List<java.lang.String> correlationIds()`

### `public java.lang.Boolean deliveryConfirmed()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public int stalledCount()`

### `public long stalledSeconds()`

### `public final java.lang.String toString()`
