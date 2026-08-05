# io.casehub.qhorus.api.channel.ChannelDetail

**Package:** `io.casehub.qhorus.api.channel`

**Kind:** `record`

## Fields

### `adminInstances` (`java.lang.String`)

### `allowedTypes` (`java.lang.String`)

### `allowedWriters` (`java.lang.String`)

### `barrierContributors` (`java.lang.String`)

### `channelId` (`java.util.UUID`)

### `connectorBinding` (`io.casehub.qhorus.api.channel.ChannelDetail.ConnectorBinding`)

### `deniedTypes` (`java.lang.String`)

### `description` (`java.lang.String`)

### `lastActivityAt` (`java.lang.String`)

### `messageCount` (`long`)

### `name` (`java.lang.String`)

### `paused` (`boolean`)

### `protocolParticipants` (`java.lang.String`)

### `protocols` (`java.lang.String`)

### `rateLimitPerChannel` (`java.lang.Integer`)

### `rateLimitPerInstance` (`java.lang.Integer`)

### `reviewerInstances` (`java.lang.String`)

### `semantic` (`java.lang.String`)

### `spaceId` (`java.util.UUID`)

### `spaceName` (`java.lang.String`)

### `trackDelivery` (`java.lang.Boolean`)

## Record Components

### `adminInstances` (`java.lang.String`)

### `allowedTypes` (`java.lang.String`)

### `allowedWriters` (`java.lang.String`)

### `barrierContributors` (`java.lang.String`)

### `channelId` (`java.util.UUID`)

### `connectorBinding` (`io.casehub.qhorus.api.channel.ChannelDetail.ConnectorBinding`)

### `deniedTypes` (`java.lang.String`)

### `description` (`java.lang.String`)

### `lastActivityAt` (`java.lang.String`)

### `messageCount` (`long`)

### `name` (`java.lang.String`)

### `paused` (`boolean`)

### `protocolParticipants` (`java.lang.String`)

### `protocols` (`java.lang.String`)

### `rateLimitPerChannel` (`java.lang.Integer`)

### `rateLimitPerInstance` (`java.lang.Integer`)

### `reviewerInstances` (`java.lang.String`)

### `semantic` (`java.lang.String`)

### `spaceId` (`java.util.UUID`)

### `spaceName` (`java.lang.String`)

### `trackDelivery` (`java.lang.Boolean`)

## Constructors

### `public ChannelDetail(java.util.UUID channelId, java.lang.String name, java.lang.String description, java.lang.String semantic, java.lang.String barrierContributors, long messageCount, java.lang.String lastActivityAt, boolean paused, java.lang.String allowedWriters, java.lang.String adminInstances, java.lang.Integer rateLimitPerChannel, java.lang.Integer rateLimitPerInstance, java.lang.String allowedTypes, java.lang.String deniedTypes, java.util.UUID spaceId, java.lang.String spaceName, java.lang.String reviewerInstances, io.casehub.qhorus.api.channel.ChannelDetail.ConnectorBinding connectorBinding)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `name` (`java.lang.String`)
- `description` (`java.lang.String`)
- `semantic` (`java.lang.String`)
- `barrierContributors` (`java.lang.String`)
- `messageCount` (`long`)
- `lastActivityAt` (`java.lang.String`)
- `paused` (`boolean`)
- `allowedWriters` (`java.lang.String`)
- `adminInstances` (`java.lang.String`)
- `rateLimitPerChannel` (`java.lang.Integer`)
- `rateLimitPerInstance` (`java.lang.Integer`)
- `allowedTypes` (`java.lang.String`)
- `deniedTypes` (`java.lang.String`)
- `spaceId` (`java.util.UUID`)
- `spaceName` (`java.lang.String`)
- `reviewerInstances` (`java.lang.String`)
- `connectorBinding` (`io.casehub.qhorus.api.channel.ChannelDetail.ConnectorBinding`)

### `public ChannelDetail(java.util.UUID channelId, java.lang.String name, java.lang.String description, java.lang.String semantic, java.lang.String barrierContributors, long messageCount, java.lang.String lastActivityAt, boolean paused, java.lang.String allowedWriters, java.lang.String adminInstances, java.lang.Integer rateLimitPerChannel, java.lang.Integer rateLimitPerInstance, java.lang.String allowedTypes, java.lang.String deniedTypes, java.util.UUID spaceId, java.lang.String spaceName, java.lang.String reviewerInstances, java.lang.String protocols, java.lang.String protocolParticipants, io.casehub.qhorus.api.channel.ChannelDetail.ConnectorBinding connectorBinding)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `name` (`java.lang.String`)
- `description` (`java.lang.String`)
- `semantic` (`java.lang.String`)
- `barrierContributors` (`java.lang.String`)
- `messageCount` (`long`)
- `lastActivityAt` (`java.lang.String`)
- `paused` (`boolean`)
- `allowedWriters` (`java.lang.String`)
- `adminInstances` (`java.lang.String`)
- `rateLimitPerChannel` (`java.lang.Integer`)
- `rateLimitPerInstance` (`java.lang.Integer`)
- `allowedTypes` (`java.lang.String`)
- `deniedTypes` (`java.lang.String`)
- `spaceId` (`java.util.UUID`)
- `spaceName` (`java.lang.String`)
- `reviewerInstances` (`java.lang.String`)
- `protocols` (`java.lang.String`)
- `protocolParticipants` (`java.lang.String`)
- `connectorBinding` (`io.casehub.qhorus.api.channel.ChannelDetail.ConnectorBinding`)

### `public ChannelDetail(java.util.UUID channelId, java.lang.String name, java.lang.String description, java.lang.String semantic, java.lang.String barrierContributors, long messageCount, java.lang.String lastActivityAt, boolean paused, java.lang.String allowedWriters, java.lang.String adminInstances, java.lang.Integer rateLimitPerChannel, java.lang.Integer rateLimitPerInstance, java.lang.String allowedTypes, java.lang.String deniedTypes, java.util.UUID spaceId, java.lang.String spaceName, java.lang.String reviewerInstances, java.lang.String protocols, java.lang.String protocolParticipants, java.lang.Boolean trackDelivery, io.casehub.qhorus.api.channel.ChannelDetail.ConnectorBinding connectorBinding)`

#### Parameters

- `channelId` (`java.util.UUID`)
- `name` (`java.lang.String`)
- `description` (`java.lang.String`)
- `semantic` (`java.lang.String`)
- `barrierContributors` (`java.lang.String`)
- `messageCount` (`long`)
- `lastActivityAt` (`java.lang.String`)
- `paused` (`boolean`)
- `allowedWriters` (`java.lang.String`)
- `adminInstances` (`java.lang.String`)
- `rateLimitPerChannel` (`java.lang.Integer`)
- `rateLimitPerInstance` (`java.lang.Integer`)
- `allowedTypes` (`java.lang.String`)
- `deniedTypes` (`java.lang.String`)
- `spaceId` (`java.util.UUID`)
- `spaceName` (`java.lang.String`)
- `reviewerInstances` (`java.lang.String`)
- `protocols` (`java.lang.String`)
- `protocolParticipants` (`java.lang.String`)
- `trackDelivery` (`java.lang.Boolean`)
- `connectorBinding` (`io.casehub.qhorus.api.channel.ChannelDetail.ConnectorBinding`)

## Methods

### `public java.lang.String adminInstances()`

### `public java.lang.String allowedTypes()`

### `public java.lang.String allowedWriters()`

### `public java.lang.String barrierContributors()`

### `public java.util.UUID channelId()`

### `public io.casehub.qhorus.api.channel.ChannelDetail.ConnectorBinding connectorBinding()`

### `public java.lang.String deniedTypes()`

### `public java.lang.String description()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.lang.String lastActivityAt()`

### `public long messageCount()`

### `public java.lang.String name()`

### `public boolean paused()`

### `public java.lang.String protocolParticipants()`

### `public java.lang.String protocols()`

### `public java.lang.Integer rateLimitPerChannel()`

### `public java.lang.Integer rateLimitPerInstance()`

### `public java.lang.String reviewerInstances()`

### `public java.lang.String semantic()`

### `public java.util.UUID spaceId()`

### `public java.lang.String spaceName()`

### `public final java.lang.String toString()`

### `public java.lang.Boolean trackDelivery()`
