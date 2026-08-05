# io.casehub.qhorus.api.watchdog.ContextPressureContext

**Package:** `io.casehub.qhorus.api.watchdog`

**Kind:** `record`

## Fields

### `actorId` (`java.lang.String`)

### `channelId` (`java.util.UUID`)

### `channelName` (`java.lang.String`)

### `contextWindowPct` (`int`)

## Record Components

### `actorId` (`java.lang.String`)

### `channelId` (`java.util.UUID`)

### `channelName` (`java.lang.String`)

### `contextWindowPct` (`int`)

## Constructors

### `public ContextPressureContext(java.util.UUID channelId, java.lang.String channelName, java.lang.String actorId, int contextWindowPct)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `channelName` (`java.lang.String`)
- `actorId` (`java.lang.String`)
- `contextWindowPct` (`int`)

## Methods

### `public java.lang.String actorId()`

### `public java.util.UUID channelId()`

### `public java.lang.String channelName()`

### `public io.casehub.qhorus.api.watchdog.WatchdogConditionType conditionType()`

### `public int contextWindowPct()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public final java.lang.String toString()`
