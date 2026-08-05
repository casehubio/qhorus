# io.casehub.qhorus.api.watchdog.ApprovalPendingContext

**Package:** `io.casehub.qhorus.api.watchdog`

**Kind:** `record`

## Fields

### `oldestExpiryAt` (`java.time.Instant`)

### `pendingCount` (`long`)

## Record Components

### `oldestExpiryAt` (`java.time.Instant`)

### `pendingCount` (`long`)

## Constructors

### `public ApprovalPendingContext(long pendingCount, java.time.Instant oldestExpiryAt)`

#### Parameters

- `pendingCount` (`long`)
- `oldestExpiryAt` (`java.time.Instant`)

## Methods

### `public io.casehub.qhorus.api.watchdog.WatchdogConditionType conditionType()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.time.Instant oldestExpiryAt()`

### `public long pendingCount()`

### `public final java.lang.String toString()`
