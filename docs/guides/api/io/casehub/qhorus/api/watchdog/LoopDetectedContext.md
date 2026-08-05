# io.casehub.qhorus.api.watchdog.LoopDetectedContext

**Package:** `io.casehub.qhorus.api.watchdog`

**Kind:** `record`

## Fields

### `channelId` (`java.util.UUID`)

### `channelName` (`java.lang.String`)

### `maxSimilarity` (`double`)

### `messageCount` (`int`)

### `sender` (`java.lang.String`)

## Record Components

### `channelId` (`java.util.UUID`)

### `channelName` (`java.lang.String`)

### `maxSimilarity` (`double`)

### `messageCount` (`int`)

### `sender` (`java.lang.String`)

## Constructors

### `public LoopDetectedContext(java.util.UUID channelId, java.lang.String channelName, java.lang.String sender, int messageCount, double maxSimilarity)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `channelName` (`java.lang.String`)
- `sender` (`java.lang.String`)
- `messageCount` (`int`)
- `maxSimilarity` (`double`)

## Methods

### `public java.util.UUID channelId()`

### `public java.lang.String channelName()`

### `public io.casehub.qhorus.api.watchdog.WatchdogConditionType conditionType()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public double maxSimilarity()`

### `public int messageCount()`

### `public java.lang.String sender()`

### `public final java.lang.String toString()`
