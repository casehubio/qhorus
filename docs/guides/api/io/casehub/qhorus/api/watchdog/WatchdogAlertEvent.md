# io.casehub.qhorus.api.watchdog.WatchdogAlertEvent

**Package:** `io.casehub.qhorus.api.watchdog`

**Kind:** `record`

## Fields

### `context` (`io.casehub.qhorus.api.watchdog.AlertContext`)

### `firedAt` (`java.time.Instant`)

### `notificationChannel` (`java.lang.String`)

### `summary` (`java.lang.String`)

### `targetName` (`java.lang.String`)

### `watchdogId` (`java.util.UUID`)

## Record Components

### `context` (`io.casehub.qhorus.api.watchdog.AlertContext`)

### `firedAt` (`java.time.Instant`)

### `notificationChannel` (`java.lang.String`)

### `summary` (`java.lang.String`)

### `targetName` (`java.lang.String`)

### `watchdogId` (`java.util.UUID`)

## Constructors

### `public WatchdogAlertEvent(java.util.UUID watchdogId, java.lang.String targetName, java.lang.String notificationChannel, java.lang.String summary, java.time.Instant firedAt, io.casehub.qhorus.api.watchdog.AlertContext context)`

#### Parameters

- `watchdogId` (`java.util.UUID`)
- `targetName` (`java.lang.String`)
- `notificationChannel` (`java.lang.String`)
- `summary` (`java.lang.String`)
- `firedAt` (`java.time.Instant`)
- `context` (`io.casehub.qhorus.api.watchdog.AlertContext`)

## Methods

### `public io.casehub.qhorus.api.watchdog.WatchdogConditionType conditionType()`

### `public io.casehub.qhorus.api.watchdog.AlertContext context()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public java.time.Instant firedAt()`

### `public final int hashCode()`

### `public java.lang.String notificationChannel()`

### `public java.lang.String summary()`

### `public java.lang.String targetName()`

### `public final java.lang.String toString()`

### `public java.util.UUID watchdogId()`
