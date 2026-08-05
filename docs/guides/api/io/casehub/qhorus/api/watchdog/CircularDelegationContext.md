# io.casehub.qhorus.api.watchdog.CircularDelegationContext

**Package:** `io.casehub.qhorus.api.watchdog`

**Kind:** `record`

## Fields

### `chainDepth` (`int`)

### `channelId` (`java.util.UUID`)

### `channelName` (`java.lang.String`)

### `correlationId` (`java.lang.String`)

### `cycle` (`java.util.List<java.lang.String>`)

## Record Components

### `chainDepth` (`int`)

### `channelId` (`java.util.UUID`)

### `channelName` (`java.lang.String`)

### `correlationId` (`java.lang.String`)

### `cycle` (`java.util.List<java.lang.String>`)

## Constructors

### `public CircularDelegationContext(java.util.UUID channelId, java.lang.String channelName, java.lang.String correlationId, java.util.List<java.lang.String> cycle, int chainDepth)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `channelName` (`java.lang.String`)
- `correlationId` (`java.lang.String`)
- `cycle` (`java.util.List<java.lang.String>`)
- `chainDepth` (`int`)

## Methods

### `public int chainDepth()`

### `public java.util.UUID channelId()`

### `public java.lang.String channelName()`

### `public io.casehub.qhorus.api.watchdog.WatchdogConditionType conditionType()`

### `public java.lang.String correlationId()`

### `public java.util.List<java.lang.String> cycle()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public final java.lang.String toString()`
