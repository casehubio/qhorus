# io.casehub.qhorus.api.spi.SummaryUpdateContext

**Package:** `io.casehub.qhorus.api.spi`

**Kind:** `record`

## Fields

### `channelId` (`java.util.UUID`)

### `channelName` (`java.lang.String`)

### `lastUpdatedMessageId` (`java.lang.Long`)

### `messageQuery` (`java.util.function.Function<io.casehub.qhorus.api.store.query.MessageQuery,java.util.List<io.casehub.qhorus.api.message.Message>>`)

### `messagesSinceLastUpdate` (`long`)

### `previousResult` (`io.casehub.qhorus.api.spi.SummaryResult`)

### `recentMessages` (`java.util.List<io.casehub.qhorus.api.message.Message>`)

### `tenancyId` (`java.lang.String`)

## Record Components

### `channelId` (`java.util.UUID`)

### `channelName` (`java.lang.String`)

### `lastUpdatedMessageId` (`java.lang.Long`)

### `messageQuery` (`java.util.function.Function<io.casehub.qhorus.api.store.query.MessageQuery,java.util.List<io.casehub.qhorus.api.message.Message>>`)

### `messagesSinceLastUpdate` (`long`)

### `previousResult` (`io.casehub.qhorus.api.spi.SummaryResult`)

### `recentMessages` (`java.util.List<io.casehub.qhorus.api.message.Message>`)

### `tenancyId` (`java.lang.String`)

## Constructors

### `public SummaryUpdateContext(java.util.UUID channelId, java.lang.String channelName, java.lang.String tenancyId, io.casehub.qhorus.api.spi.SummaryResult previousResult, java.lang.Long lastUpdatedMessageId, long messagesSinceLastUpdate, java.util.List<io.casehub.qhorus.api.message.Message> recentMessages, java.util.function.Function<io.casehub.qhorus.api.store.query.MessageQuery,java.util.List<io.casehub.qhorus.api.message.Message>> messageQuery)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `channelName` (`java.lang.String`)
- `tenancyId` (`java.lang.String`)
- `previousResult` (`io.casehub.qhorus.api.spi.SummaryResult`)
- `lastUpdatedMessageId` (`java.lang.Long`)
- `messagesSinceLastUpdate` (`long`)
- `recentMessages` (`java.util.List<io.casehub.qhorus.api.message.Message>`)
- `messageQuery` (`java.util.function.Function<io.casehub.qhorus.api.store.query.MessageQuery,java.util.List<io.casehub.qhorus.api.message.Message>>`)

## Methods

### `public java.util.UUID channelId()`

### `public java.lang.String channelName()`

### `public java.lang.String currentSummary()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.lang.Long lastUpdatedMessageId()`

### `public java.util.function.Function<io.casehub.qhorus.api.store.query.MessageQuery,java.util.List<io.casehub.qhorus.api.message.Message>> messageQuery()`

### `public long messagesSinceLastUpdate()`

### `public io.casehub.qhorus.api.spi.SummaryResult previousResult()`

### `public java.util.List<io.casehub.qhorus.api.message.Message> recentMessages()`

### `public java.lang.String tenancyId()`

### `public final java.lang.String toString()`
