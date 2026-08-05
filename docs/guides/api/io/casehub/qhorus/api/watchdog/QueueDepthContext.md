# io.casehub.qhorus.api.watchdog.QueueDepthContext

**Package:** `io.casehub.qhorus.api.watchdog`

**Kind:** `record`

## Fields

### `channelName` (`java.lang.String`)

### `messageCount` (`long`)

### `threshold` (`int`)

## Record Components

### `channelName` (`java.lang.String`)

### `messageCount` (`long`)

### `threshold` (`int`)

## Constructors

### `public QueueDepthContext(java.lang.String channelName, long messageCount, int threshold)`

#### Parameters

- `channelName` (`java.lang.String`)
- `messageCount` (`long`)
- `threshold` (`int`)

## Methods

### `public java.lang.String channelName()`

### `public io.casehub.qhorus.api.watchdog.WatchdogConditionType conditionType()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public long messageCount()`

### `public int threshold()`

### `public final java.lang.String toString()`
