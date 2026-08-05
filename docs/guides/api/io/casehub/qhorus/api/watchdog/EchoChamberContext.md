# io.casehub.qhorus.api.watchdog.EchoChamberContext

**Package:** `io.casehub.qhorus.api.watchdog`

**Kind:** `record`

## Fields

### `channelId` (`java.util.UUID`)

### `channelName` (`java.lang.String`)

### `maxSimilarity` (`double`)

### `participants` (`java.util.List<java.lang.String>`)

## Record Components

### `channelId` (`java.util.UUID`)

### `channelName` (`java.lang.String`)

### `maxSimilarity` (`double`)

### `participants` (`java.util.List<java.lang.String>`)

## Constructors

### `public EchoChamberContext(java.util.UUID channelId, java.lang.String channelName, java.util.List<java.lang.String> participants, double maxSimilarity)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `channelName` (`java.lang.String`)
- `participants` (`java.util.List<java.lang.String>`)
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

### `public java.util.List<java.lang.String> participants()`

### `public final java.lang.String toString()`
