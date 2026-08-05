# io.casehub.qhorus.api.watchdog.ObligationFanOutContext

**Package:** `io.casehub.qhorus.api.watchdog`

**Kind:** `record`

## Fields

### `channelId` (`java.util.UUID`)

### `channelName` (`java.lang.String`)

### `correlationIds` (`java.util.List<java.lang.String>`)

### `staleCount` (`int`)

## Record Components

### `channelId` (`java.util.UUID`)

### `channelName` (`java.lang.String`)

### `correlationIds` (`java.util.List<java.lang.String>`)

### `staleCount` (`int`)

## Constructors

### `public ObligationFanOutContext(java.util.UUID channelId, java.lang.String channelName, int staleCount, java.util.List<java.lang.String> correlationIds)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `channelName` (`java.lang.String`)
- `staleCount` (`int`)
- `correlationIds` (`java.util.List<java.lang.String>`)

## Methods

### `public java.util.UUID channelId()`

### `public java.lang.String channelName()`

### `public io.casehub.qhorus.api.watchdog.WatchdogConditionType conditionType()`

### `public java.util.List<java.lang.String> correlationIds()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public int staleCount()`

### `public final java.lang.String toString()`
