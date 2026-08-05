# io.casehub.qhorus.api.store.query.DataQuery

**Package:** `io.casehub.qhorus.api.store.query`

**Kind:** `class`

## Fields

### `complete` (`java.lang.Boolean`)

### `createdBy` (`java.lang.String`)

## Constructors

### `private DataQuery(io.casehub.qhorus.api.store.query.DataQuery.Builder b)`

#### Parameters

- `b` (`io.casehub.qhorus.api.store.query.DataQuery.Builder`)

## Methods

### `public static io.casehub.qhorus.api.store.query.DataQuery all()`

### `public static io.casehub.qhorus.api.store.query.DataQuery.Builder builder()`

### `public static io.casehub.qhorus.api.store.query.DataQuery byCreator(java.lang.String instanceId)`

#### Parameters

- `instanceId` (`java.lang.String`)

### `public java.lang.Boolean complete()`

### `public static io.casehub.qhorus.api.store.query.DataQuery completeOnly()`

### `public java.lang.String createdBy()`

### `public boolean matches(io.casehub.qhorus.api.data.SharedData d)`

#### Parameters

- `d` (`io.casehub.qhorus.api.data.SharedData`)

### `public io.casehub.qhorus.api.store.query.DataQuery.Builder toBuilder()`
