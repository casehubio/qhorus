# io.casehub.qhorus.api.store.query.ChannelQuery

**Package:** `io.casehub.qhorus.api.store.query`

**Kind:** `class`

## Fields

### `keyword` (`java.lang.String`)

### `namePattern` (`java.lang.String`)

### `namePrefix` (`java.lang.String`)

### `paused` (`java.lang.Boolean`)

### `semantic` (`io.casehub.qhorus.api.channel.ChannelSemantic`)

### `spaceId` (`java.util.UUID`)

### `topLevelOnly` (`boolean`)

## Constructors

### `private ChannelQuery(io.casehub.qhorus.api.store.query.ChannelQuery.Builder b)`

#### Parameters

- `b` (`io.casehub.qhorus.api.store.query.ChannelQuery.Builder`)

## Methods

### `public static io.casehub.qhorus.api.store.query.ChannelQuery all()`

### `public static io.casehub.qhorus.api.store.query.ChannelQuery.Builder builder()`

### `public static io.casehub.qhorus.api.store.query.ChannelQuery byKeyword(java.lang.String keyword)`

#### Parameters

- `keyword` (`java.lang.String`)

### `public static io.casehub.qhorus.api.store.query.ChannelQuery byName(java.lang.String pattern)`

#### Parameters

- `pattern` (`java.lang.String`)

### `public static io.casehub.qhorus.api.store.query.ChannelQuery byNamePrefix(java.lang.String prefix)`

#### Parameters

- `prefix` (`java.lang.String`)

### `public static io.casehub.qhorus.api.store.query.ChannelQuery bySemantic(io.casehub.qhorus.api.channel.ChannelSemantic s)`

#### Parameters

- `s` (`io.casehub.qhorus.api.channel.ChannelSemantic`)

### `public static io.casehub.qhorus.api.store.query.ChannelQuery bySpaceId(java.util.UUID spaceId)`

#### Parameters

- `spaceId` (`java.util.UUID`)

### `public java.lang.String keyword()`

### `public boolean matches(io.casehub.qhorus.api.channel.Channel ch)`

#### Parameters

- `ch` (`io.casehub.qhorus.api.channel.Channel`)

### `public java.lang.String namePattern()`

### `public java.lang.String namePrefix()`

### `public java.lang.Boolean paused()`

### `public static io.casehub.qhorus.api.store.query.ChannelQuery pausedOnly()`

### `public io.casehub.qhorus.api.channel.ChannelSemantic semantic()`

### `public java.util.UUID spaceId()`

### `public io.casehub.qhorus.api.store.query.ChannelQuery.Builder toBuilder()`

### `public static io.casehub.qhorus.api.store.query.ChannelQuery topLevel()`

### `public boolean topLevelOnly()`
