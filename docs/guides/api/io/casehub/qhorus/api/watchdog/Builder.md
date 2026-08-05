# io.casehub.qhorus.api.watchdog.Watchdog.Builder

**Package:** `io.casehub.qhorus.api.watchdog`

**Kind:** `class`

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

## Constructors

### `private Builder(io.casehub.qhorus.api.watchdog.WatchdogConditionType conditionType, java.lang.String targetName)`

#### Parameters

- `conditionType` (`io.casehub.qhorus.api.watchdog.WatchdogConditionType`)
- `targetName` (`java.lang.String`)

## Methods

### `public io.casehub.qhorus.api.watchdog.Watchdog build()`

### `public io.casehub.qhorus.api.watchdog.Watchdog.Builder createdAt(java.time.Instant v)`

#### Parameters

- `v` (`java.time.Instant`)

### `public io.casehub.qhorus.api.watchdog.Watchdog.Builder createdBy(java.lang.String v)`

#### Parameters

- `v` (`java.lang.String`)

### `public io.casehub.qhorus.api.watchdog.Watchdog.Builder id(java.util.UUID v)`

#### Parameters

- `v` (`java.util.UUID`)

### `public io.casehub.qhorus.api.watchdog.Watchdog.Builder lastFiredAt(java.time.Instant v)`

#### Parameters

- `v` (`java.time.Instant`)

### `public io.casehub.qhorus.api.watchdog.Watchdog.Builder notificationChannel(java.lang.String v)`

#### Parameters

- `v` (`java.lang.String`)

### `public io.casehub.qhorus.api.watchdog.Watchdog.Builder similarityPct(java.lang.Integer v)`

#### Parameters

- `v` (`java.lang.Integer`)

### `public io.casehub.qhorus.api.watchdog.Watchdog.Builder tenancyId(java.lang.String v)`

#### Parameters

- `v` (`java.lang.String`)

### `public io.casehub.qhorus.api.watchdog.Watchdog.Builder thresholdCount(java.lang.Integer v)`

#### Parameters

- `v` (`java.lang.Integer`)

### `public io.casehub.qhorus.api.watchdog.Watchdog.Builder thresholdSeconds(java.lang.Integer v)`

#### Parameters

- `v` (`java.lang.Integer`)
