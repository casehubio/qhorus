# io.casehub.qhorus.api.watchdog.BarrierStuckContext

**Package:** `io.casehub.qhorus.api.watchdog`

**Kind:** `record`

## Fields

### `channelId` (`java.util.UUID`)

### `channelName` (`java.lang.String`)

### `deliveredNoResponse` (`java.util.List<java.lang.String>`)

### `elapsedSeconds` (`long`)

### `missingContributors` (`java.util.List<java.lang.String>`)

### `notDelivered` (`java.util.List<java.lang.String>`)

## Record Components

### `channelId` (`java.util.UUID`)

### `channelName` (`java.lang.String`)

### `deliveredNoResponse` (`java.util.List<java.lang.String>`)

### `elapsedSeconds` (`long`)

### `missingContributors` (`java.util.List<java.lang.String>`)

### `notDelivered` (`java.util.List<java.lang.String>`)

## Constructors

### `public BarrierStuckContext(java.util.UUID channelId, java.lang.String channelName, java.util.List<java.lang.String> missingContributors, java.util.List<java.lang.String> notDelivered, java.util.List<java.lang.String> deliveredNoResponse, long elapsedSeconds)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `channelName` (`java.lang.String`)
- `missingContributors` (`java.util.List<java.lang.String>`)
- `notDelivered` (`java.util.List<java.lang.String>`)
- `deliveredNoResponse` (`java.util.List<java.lang.String>`)
- `elapsedSeconds` (`long`)

### `public BarrierStuckContext(java.util.UUID channelId, java.lang.String channelName, java.util.List<java.lang.String> missingContributors, long elapsedSeconds)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `channelName` (`java.lang.String`)
- `missingContributors` (`java.util.List<java.lang.String>`)
- `elapsedSeconds` (`long`)

## Methods

### `public java.util.UUID channelId()`

### `public java.lang.String channelName()`

### `public io.casehub.qhorus.api.watchdog.WatchdogConditionType conditionType()`

### `public java.util.List<java.lang.String> deliveredNoResponse()`

### `public long elapsedSeconds()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.util.List<java.lang.String> missingContributors()`

### `public java.util.List<java.lang.String> notDelivered()`

### `public final java.lang.String toString()`
