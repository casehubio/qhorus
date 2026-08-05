# io.casehub.qhorus.api.store.query.InstanceQuery

**Package:** `io.casehub.qhorus.api.store.query`

**Kind:** `class`

## Fields

### `capability` (`java.lang.String`)

### `staleOlderThan` (`java.time.Instant`)

### `status` (`java.lang.String`)

## Constructors

### `private InstanceQuery(io.casehub.qhorus.api.store.query.InstanceQuery.Builder b)`

#### Parameters

- `b` (`io.casehub.qhorus.api.store.query.InstanceQuery.Builder`)

## Methods

### `public static io.casehub.qhorus.api.store.query.InstanceQuery all()`

### `public static io.casehub.qhorus.api.store.query.InstanceQuery.Builder builder()`

### `public static io.casehub.qhorus.api.store.query.InstanceQuery byCapability(java.lang.String tag)`

#### Parameters

- `tag` (`java.lang.String`)

### `public java.lang.String capability()`

### `public boolean matches(io.casehub.qhorus.api.instance.Instance inst)`

Matches an Instance against this query's scalar predicates.
Capability filtering requires a join to the Capability table and is applied by the store.

#### Parameters

- `inst` (`io.casehub.qhorus.api.instance.Instance`)

### `public static io.casehub.qhorus.api.store.query.InstanceQuery online()`

### `public java.time.Instant staleOlderThan()`

### `public static io.casehub.qhorus.api.store.query.InstanceQuery staleOlderThan(java.time.Instant threshold)`

#### Parameters

- `threshold` (`java.time.Instant`)

### `public java.lang.String status()`

### `public io.casehub.qhorus.api.store.query.InstanceQuery.Builder toBuilder()`
