# io.casehub.qhorus.api.channel.TopicManager

**Package:** `io.casehub.qhorus.api.channel`

**Kind:** `interface`

## Methods

### `public abstract io.casehub.qhorus.api.message.Topic create(java.util.UUID channelId, java.lang.String name)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `name` (`java.lang.String`)

### `public abstract java.util.List<io.casehub.qhorus.api.message.TopicSummary> listTopics(java.util.UUID channelId)`

#### Parameters

- `channelId` (`java.util.UUID`)

### `public abstract io.casehub.qhorus.api.channel.TopicManager.MergeResult merge(java.util.UUID channelId, java.lang.String sourceTopic, java.lang.String targetTopic)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `sourceTopic` (`java.lang.String`)
- `targetTopic` (`java.lang.String`)

### `public abstract io.casehub.qhorus.api.channel.TopicManager.RenameResult rename(java.util.UUID channelId, java.lang.String oldName, java.lang.String newName)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `oldName` (`java.lang.String`)
- `newName` (`java.lang.String`)

### `public abstract io.casehub.qhorus.api.message.Topic resolve(java.util.UUID channelId, java.lang.String topicName)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `topicName` (`java.lang.String`)

### `public abstract io.casehub.qhorus.api.message.Topic unresolve(java.util.UUID channelId, java.lang.String topicName)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `topicName` (`java.lang.String`)
