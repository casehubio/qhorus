# io.casehub.qhorus.api.store.query.MessageQuery

**Package:** `io.casehub.qhorus.api.store.query`

**Kind:** `class`

## Fields

### `afterId` (`java.lang.Long`)

### `afterVersion` (`java.lang.Integer`)

### `channelId` (`java.util.UUID`)

### `contentPattern` (`java.lang.String`)

### `correlationId` (`java.lang.String`)

### `descending` (`boolean`)

### `excludeTypes` (`java.util.List<io.casehub.qhorus.api.message.MessageType>`)

### `inReplyTo` (`java.lang.Long`)

### `limit` (`java.lang.Integer`)

### `messageType` (`io.casehub.qhorus.api.message.MessageType`)

### `sender` (`java.lang.String`)

### `target` (`java.lang.String`)

### `topic` (`java.lang.String`)

## Constructors

### `private MessageQuery(io.casehub.qhorus.api.store.query.MessageQuery.Builder b)`

#### Parameters

- `b` (`io.casehub.qhorus.api.store.query.MessageQuery.Builder`)

## Methods

### `public java.lang.Long afterId()`

### `public java.lang.Integer afterVersion()`

### `public static io.casehub.qhorus.api.store.query.MessageQuery.Builder builder()`

### `public java.util.UUID channelId()`

### `public java.lang.String contentPattern()`

### `public java.lang.String correlationId()`

### `public boolean descending()`

### `public java.util.List<io.casehub.qhorus.api.message.MessageType> excludeTypes()`

### `public static io.casehub.qhorus.api.store.query.MessageQuery forChannel(java.util.UUID channelId)`

#### Parameters

- `channelId` (`java.util.UUID`)

### `public java.lang.Long inReplyTo()`

### `public java.lang.Integer limit()`

### `public boolean matches(io.casehub.qhorus.api.message.Message m)`

#### Parameters

- `m` (`io.casehub.qhorus.api.message.Message`)

### `public io.casehub.qhorus.api.message.MessageType messageType()`

### `public static io.casehub.qhorus.api.store.query.MessageQuery poll(java.util.UUID channelId, java.lang.Long afterId, int limit)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `afterId` (`java.lang.Long`)
- `limit` (`int`)

### `public static io.casehub.qhorus.api.store.query.MessageQuery recent(int limit)`

#### Parameters

- `limit` (`int`)

### `public static io.casehub.qhorus.api.store.query.MessageQuery replies(java.util.UUID channelId, java.lang.Long inReplyTo)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `inReplyTo` (`java.lang.Long`)

### `public java.lang.String sender()`

### `public java.lang.String target()`

### `public io.casehub.qhorus.api.store.query.MessageQuery.Builder toBuilder()`

### `public java.lang.String topic()`
