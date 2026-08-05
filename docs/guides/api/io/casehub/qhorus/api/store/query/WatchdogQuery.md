# io.casehub.qhorus.api.store.query.WatchdogQuery

**Package:** `io.casehub.qhorus.api.store.query`

**Kind:** `class`

## Fields

### `conditionType` (`io.casehub.qhorus.api.watchdog.WatchdogConditionType`)

### `tenancyId` (`java.lang.String`)

## Constructors

### `private WatchdogQuery(io.casehub.qhorus.api.store.query.WatchdogQuery.Builder b)`

#### Parameters

- `b` (`io.casehub.qhorus.api.store.query.WatchdogQuery.Builder`)

## Methods

### `public static io.casehub.qhorus.api.store.query.WatchdogQuery all()`

### `public static io.casehub.qhorus.api.store.query.WatchdogQuery.Builder builder()`

### `public static io.casehub.qhorus.api.store.query.WatchdogQuery byConditionType(io.casehub.qhorus.api.watchdog.WatchdogConditionType conditionType)`

#### Parameters

- `conditionType` (`io.casehub.qhorus.api.watchdog.WatchdogConditionType`)

### `public static io.casehub.qhorus.api.store.query.WatchdogQuery byTenancy(java.lang.String tenancyId)`

#### Parameters

- `tenancyId` (`java.lang.String`)

### `public io.casehub.qhorus.api.watchdog.WatchdogConditionType conditionType()`

### `public boolean matches(io.casehub.qhorus.api.watchdog.Watchdog w)`

#### Parameters

- `w` (`io.casehub.qhorus.api.watchdog.Watchdog`)

### `public java.lang.String tenancyId()`

### `public io.casehub.qhorus.api.store.query.WatchdogQuery.Builder toBuilder()`
