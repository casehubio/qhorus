# io.casehub.qhorus.api.gateway.MessageObserver.Scope

**Package:** `io.casehub.qhorus.api.gateway`

**Kind:** `enum`

## Enum Constants

### `CLUSTER` (`io.casehub.qhorus.api.gateway.MessageObserver.Scope`)

Crosses process/machine boundaries via a network transport.

### `LOCAL` (`io.casehub.qhorus.api.gateway.MessageObserver.Scope`)

In-JVM only. Zero serialisation, zero network overhead.

## Constructors

### `private Scope()`

## Methods

### `public static io.casehub.qhorus.api.gateway.MessageObserver.Scope valueOf(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public static io.casehub.qhorus.api.gateway.MessageObserver.Scope[] values()`
