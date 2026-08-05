# io.casehub.qhorus.api.watchdog.Watchdog

**Package:** `io.casehub.qhorus.api.watchdog`

**Kind:** `record`

## Fields

### `conditionType` (`io.casehub.qhorus.api.watchdog.WatchdogConditionType`)

### `createdAt` (`java.time.Instant`)

### `createdBy` (`java.lang.String`)

### `id` (`java.util.UUID`)

### `lastFiredAt` (`java.time.Instant`)

### `notificationChannel` (`java.lang.String`)

### `similarityPct` (`java.lang.Integer`)

### `targetName` (`java.lang.String`)

### `tenancyId` (`java.lang.String`)

### `thresholdCount` (`java.lang.Integer`)

### `thresholdSeconds` (`java.lang.Integer`)

## Record Components

### `conditionType` (`io.casehub.qhorus.api.watchdog.WatchdogConditionType`)

### `createdAt` (`java.time.Instant`)

### `createdBy` (`java.lang.String`)

### `id` (`java.util.UUID`)

### `lastFiredAt` (`java.time.Instant`)

### `notificationChannel` (`java.lang.String`)

### `similarityPct` (`java.lang.Integer`)

### `targetName` (`java.lang.String`)

### `tenancyId` (`java.lang.String`)

### `thresholdCount` (`java.lang.Integer`)

### `thresholdSeconds` (`java.lang.Integer`)

## Constructors

### `public Watchdog(java.util.UUID id, io.casehub.qhorus.api.watchdog.WatchdogConditionType conditionType, java.lang.String targetName, java.lang.Integer thresholdSeconds, java.lang.Integer thresholdCount, java.lang.Integer similarityPct, java.lang.String notificationChannel, java.lang.String createdBy, java.lang.String tenancyId, java.time.Instant createdAt, java.time.Instant lastFiredAt)`

#### Parameters

- `id` (`java.util.UUID`)
- `conditionType` (`io.casehub.qhorus.api.watchdog.WatchdogConditionType`)
- `targetName` (`java.lang.String`)
- `thresholdSeconds` (`java.lang.Integer`)
- `thresholdCount` (`java.lang.Integer`)
- `similarityPct` (`java.lang.Integer`)
- `notificationChannel` (`java.lang.String`)
- `createdBy` (`java.lang.String`)
- `tenancyId` (`java.lang.String`)
- `createdAt` (`java.time.Instant`)
- `lastFiredAt` (`java.time.Instant`)

## Methods

### `public static io.casehub.qhorus.api.watchdog.Watchdog.Builder builder(io.casehub.qhorus.api.watchdog.WatchdogConditionType conditionType, java.lang.String targetName)`

#### Parameters

- `conditionType` (`io.casehub.qhorus.api.watchdog.WatchdogConditionType`)
- `targetName` (`java.lang.String`)

### `public io.casehub.qhorus.api.watchdog.WatchdogConditionType conditionType()`

### `public java.time.Instant createdAt()`

### `public java.lang.String createdBy()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.util.UUID id()`

### `public java.time.Instant lastFiredAt()`

### `public java.lang.String notificationChannel()`

### `public java.lang.Integer similarityPct()`

### `public java.lang.String targetName()`

### `public java.lang.String tenancyId()`

### `public java.lang.Integer thresholdCount()`

### `public java.lang.Integer thresholdSeconds()`

### `public io.casehub.qhorus.api.watchdog.Watchdog.Builder toBuilder()`

### `public final java.lang.String toString()`
