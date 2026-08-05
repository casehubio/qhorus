# io.casehub.qhorus.api.message.ConsumerMessaging

**Package:** `io.casehub.qhorus.api.message`

**Kind:** `interface`

## Methods

### `public abstract java.util.List<io.casehub.qhorus.api.message.Message> findAllByCorrelationId(java.lang.String correlationId)`

#### Parameters

- `correlationId` (`java.lang.String`)

### `public abstract java.util.Optional<io.casehub.qhorus.api.message.Message> findByCorrelationId(java.lang.String correlationId)`

#### Parameters

- `correlationId` (`java.lang.String`)

### `public abstract java.util.Optional<io.casehub.qhorus.api.message.Message> findById(java.lang.Long messageId)`

#### Parameters

- `messageId` (`java.lang.Long`)

### `public abstract java.util.List<io.casehub.qhorus.api.message.Message> history(java.util.UUID channelId, long afterId, int limit)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `afterId` (`long`)
- `limit` (`int`)

### `public abstract java.util.List<io.casehub.qhorus.api.message.Message> history(java.util.UUID channelId, long afterId, int limit, boolean includeEvents)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `afterId` (`long`)
- `limit` (`int`)
- `includeEvents` (`boolean`)

### `public abstract java.util.List<io.casehub.qhorus.api.message.Message> historyBySender(java.util.UUID channelId, long afterId, int limit, java.lang.String sender, boolean includeEvents)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `afterId` (`long`)
- `limit` (`int`)
- `sender` (`java.lang.String`)
- `includeEvents` (`boolean`)
