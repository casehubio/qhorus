# io.casehub.qhorus.api.channel.Channel

**Package:** `io.casehub.qhorus.api.channel`

**Kind:** `record`

## Fields

### `adminInstances` (`java.util.List<java.lang.String>`)

### `allowedTypes` (`java.util.Set<io.casehub.qhorus.api.message.MessageType>`)

### `allowedWriters` (`java.util.List<java.lang.String>`)

### `autoCreated` (`boolean`)

### `barrierContributors` (`java.util.List<java.lang.String>`)

### `createdAt` (`java.time.Instant`)

### `deniedTypes` (`java.util.Set<io.casehub.qhorus.api.message.MessageType>`)

### `description` (`java.lang.String`)

### `id` (`java.util.UUID`)

### `lastActivityAt` (`java.time.Instant`)

### `name` (`java.lang.String`)

### `paused` (`boolean`)

### `protocolParticipants` (`java.util.List<java.lang.String>`)

### `protocols` (`java.util.List<java.lang.String>`)

### `rateLimitPerChannel` (`java.lang.Integer`)

### `rateLimitPerInstance` (`java.lang.Integer`)

### `reviewerInstances` (`java.util.List<java.lang.String>`)

### `semantic` (`io.casehub.qhorus.api.channel.ChannelSemantic`)

### `spaceId` (`java.util.UUID`)

### `tenancyId` (`java.lang.String`)

### `trackDelivery` (`java.lang.Boolean`)

## Record Components

### `adminInstances` (`java.util.List<java.lang.String>`)

### `allowedTypes` (`java.util.Set<io.casehub.qhorus.api.message.MessageType>`)

### `allowedWriters` (`java.util.List<java.lang.String>`)

### `autoCreated` (`boolean`)

### `barrierContributors` (`java.util.List<java.lang.String>`)

### `createdAt` (`java.time.Instant`)

### `deniedTypes` (`java.util.Set<io.casehub.qhorus.api.message.MessageType>`)

### `description` (`java.lang.String`)

### `id` (`java.util.UUID`)

### `lastActivityAt` (`java.time.Instant`)

### `name` (`java.lang.String`)

### `paused` (`boolean`)

### `protocolParticipants` (`java.util.List<java.lang.String>`)

### `protocols` (`java.util.List<java.lang.String>`)

### `rateLimitPerChannel` (`java.lang.Integer`)

### `rateLimitPerInstance` (`java.lang.Integer`)

### `reviewerInstances` (`java.util.List<java.lang.String>`)

### `semantic` (`io.casehub.qhorus.api.channel.ChannelSemantic`)

### `spaceId` (`java.util.UUID`)

### `tenancyId` (`java.lang.String`)

### `trackDelivery` (`java.lang.Boolean`)

## Constructors

### `public Channel(java.util.UUID id, java.lang.String name, java.lang.String description, io.casehub.qhorus.api.channel.ChannelSemantic semantic, java.util.List<java.lang.String> barrierContributors, java.util.List<java.lang.String> allowedWriters, java.util.List<java.lang.String> adminInstances, java.lang.Integer rateLimitPerChannel, java.lang.Integer rateLimitPerInstance, java.util.Set<io.casehub.qhorus.api.message.MessageType> allowedTypes, java.util.Set<io.casehub.qhorus.api.message.MessageType> deniedTypes, boolean paused, boolean autoCreated, java.util.UUID spaceId, java.lang.String tenancyId, java.time.Instant createdAt, java.time.Instant lastActivityAt)`

#### Parameters

- `id` (`java.util.UUID`)
- `name` (`java.lang.String`)
- `description` (`java.lang.String`)
- `semantic` (`io.casehub.qhorus.api.channel.ChannelSemantic`)
- `barrierContributors` (`java.util.List<java.lang.String>`)
- `allowedWriters` (`java.util.List<java.lang.String>`)
- `adminInstances` (`java.util.List<java.lang.String>`)
- `rateLimitPerChannel` (`java.lang.Integer`)
- `rateLimitPerInstance` (`java.lang.Integer`)
- `allowedTypes` (`java.util.Set<io.casehub.qhorus.api.message.MessageType>`)
- `deniedTypes` (`java.util.Set<io.casehub.qhorus.api.message.MessageType>`)
- `paused` (`boolean`)
- `autoCreated` (`boolean`)
- `spaceId` (`java.util.UUID`)
- `tenancyId` (`java.lang.String`)
- `createdAt` (`java.time.Instant`)
- `lastActivityAt` (`java.time.Instant`)

### `public Channel(java.util.UUID id, java.lang.String name, java.lang.String description, io.casehub.qhorus.api.channel.ChannelSemantic semantic, java.util.List<java.lang.String> barrierContributors, java.util.List<java.lang.String> allowedWriters, java.util.List<java.lang.String> adminInstances, java.lang.Integer rateLimitPerChannel, java.lang.Integer rateLimitPerInstance, java.util.Set<io.casehub.qhorus.api.message.MessageType> allowedTypes, java.util.Set<io.casehub.qhorus.api.message.MessageType> deniedTypes, boolean paused, boolean autoCreated, java.util.UUID spaceId, java.util.List<java.lang.String> reviewerInstances, java.lang.String tenancyId, java.time.Instant createdAt, java.time.Instant lastActivityAt)`

#### Parameters

- `id` (`java.util.UUID`)
- `name` (`java.lang.String`)
- `description` (`java.lang.String`)
- `semantic` (`io.casehub.qhorus.api.channel.ChannelSemantic`)
- `barrierContributors` (`java.util.List<java.lang.String>`)
- `allowedWriters` (`java.util.List<java.lang.String>`)
- `adminInstances` (`java.util.List<java.lang.String>`)
- `rateLimitPerChannel` (`java.lang.Integer`)
- `rateLimitPerInstance` (`java.lang.Integer`)
- `allowedTypes` (`java.util.Set<io.casehub.qhorus.api.message.MessageType>`)
- `deniedTypes` (`java.util.Set<io.casehub.qhorus.api.message.MessageType>`)
- `paused` (`boolean`)
- `autoCreated` (`boolean`)
- `spaceId` (`java.util.UUID`)
- `reviewerInstances` (`java.util.List<java.lang.String>`)
- `tenancyId` (`java.lang.String`)
- `createdAt` (`java.time.Instant`)
- `lastActivityAt` (`java.time.Instant`)

### `public Channel(java.util.UUID id, java.lang.String name, java.lang.String description, io.casehub.qhorus.api.channel.ChannelSemantic semantic, java.util.List<java.lang.String> barrierContributors, java.util.List<java.lang.String> allowedWriters, java.util.List<java.lang.String> adminInstances, java.lang.Integer rateLimitPerChannel, java.lang.Integer rateLimitPerInstance, java.util.Set<io.casehub.qhorus.api.message.MessageType> allowedTypes, java.util.Set<io.casehub.qhorus.api.message.MessageType> deniedTypes, boolean paused, boolean autoCreated, java.util.UUID spaceId, java.util.List<java.lang.String> reviewerInstances, java.util.List<java.lang.String> protocols, java.util.List<java.lang.String> protocolParticipants, java.lang.Boolean trackDelivery, java.lang.String tenancyId, java.time.Instant createdAt, java.time.Instant lastActivityAt)`

#### Parameters

- `id` (`java.util.UUID`)
- `name` (`java.lang.String`)
- `description` (`java.lang.String`)
- `semantic` (`io.casehub.qhorus.api.channel.ChannelSemantic`)
- `barrierContributors` (`java.util.List<java.lang.String>`)
- `allowedWriters` (`java.util.List<java.lang.String>`)
- `adminInstances` (`java.util.List<java.lang.String>`)
- `rateLimitPerChannel` (`java.lang.Integer`)
- `rateLimitPerInstance` (`java.lang.Integer`)
- `allowedTypes` (`java.util.Set<io.casehub.qhorus.api.message.MessageType>`)
- `deniedTypes` (`java.util.Set<io.casehub.qhorus.api.message.MessageType>`)
- `paused` (`boolean`)
- `autoCreated` (`boolean`)
- `spaceId` (`java.util.UUID`)
- `reviewerInstances` (`java.util.List<java.lang.String>`)
- `protocols` (`java.util.List<java.lang.String>`)
- `protocolParticipants` (`java.util.List<java.lang.String>`)
- `trackDelivery` (`java.lang.Boolean`)
- `tenancyId` (`java.lang.String`)
- `createdAt` (`java.time.Instant`)
- `lastActivityAt` (`java.time.Instant`)

### `public Channel(java.util.UUID id, java.lang.String name, java.lang.String description, io.casehub.qhorus.api.channel.ChannelSemantic semantic, java.util.List<java.lang.String> barrierContributors, java.util.List<java.lang.String> allowedWriters, java.util.List<java.lang.String> adminInstances, java.lang.Integer rateLimitPerChannel, java.lang.Integer rateLimitPerInstance, java.util.Set<io.casehub.qhorus.api.message.MessageType> allowedTypes, java.util.Set<io.casehub.qhorus.api.message.MessageType> deniedTypes, boolean paused, boolean autoCreated, java.util.UUID spaceId, java.util.List<java.lang.String> reviewerInstances, java.util.List<java.lang.String> protocols, java.util.List<java.lang.String> protocolParticipants, java.lang.String tenancyId, java.time.Instant createdAt, java.time.Instant lastActivityAt)`

#### Parameters

- `id` (`java.util.UUID`)
- `name` (`java.lang.String`)
- `description` (`java.lang.String`)
- `semantic` (`io.casehub.qhorus.api.channel.ChannelSemantic`)
- `barrierContributors` (`java.util.List<java.lang.String>`)
- `allowedWriters` (`java.util.List<java.lang.String>`)
- `adminInstances` (`java.util.List<java.lang.String>`)
- `rateLimitPerChannel` (`java.lang.Integer`)
- `rateLimitPerInstance` (`java.lang.Integer`)
- `allowedTypes` (`java.util.Set<io.casehub.qhorus.api.message.MessageType>`)
- `deniedTypes` (`java.util.Set<io.casehub.qhorus.api.message.MessageType>`)
- `paused` (`boolean`)
- `autoCreated` (`boolean`)
- `spaceId` (`java.util.UUID`)
- `reviewerInstances` (`java.util.List<java.lang.String>`)
- `protocols` (`java.util.List<java.lang.String>`)
- `protocolParticipants` (`java.util.List<java.lang.String>`)
- `tenancyId` (`java.lang.String`)
- `createdAt` (`java.time.Instant`)
- `lastActivityAt` (`java.time.Instant`)

## Methods

### `public java.util.List<java.lang.String> adminInstances()`

### `public java.util.Set<io.casehub.qhorus.api.message.MessageType> allowedTypes()`

### `public java.util.List<java.lang.String> allowedWriters()`

### `public boolean autoCreated()`

### `public java.util.List<java.lang.String> barrierContributors()`

### `public static io.casehub.qhorus.api.channel.Channel.Builder builder(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public java.time.Instant createdAt()`

### `public java.util.Set<io.casehub.qhorus.api.message.MessageType> deniedTypes()`

### `public java.lang.String description()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public static io.casehub.qhorus.api.channel.Channel fromRequest(io.casehub.qhorus.api.channel.ChannelCreateRequest req, java.lang.String tenancyId)`

#### Parameters

- `req` (`io.casehub.qhorus.api.channel.ChannelCreateRequest`)
- `tenancyId` (`java.lang.String`)

### `public final int hashCode()`

### `public java.util.UUID id()`

### `public java.time.Instant lastActivityAt()`

### `public java.lang.String name()`

### `public boolean paused()`

### `public java.util.List<java.lang.String> protocolParticipants()`

### `public java.util.List<java.lang.String> protocols()`

### `public java.lang.Integer rateLimitPerChannel()`

### `public java.lang.Integer rateLimitPerInstance()`

### `public java.util.List<java.lang.String> reviewerInstances()`

### `public io.casehub.qhorus.api.channel.ChannelSemantic semantic()`

### `public java.util.UUID spaceId()`

### `public java.lang.String tenancyId()`

### `public io.casehub.qhorus.api.channel.Channel.Builder toBuilder()`

### `public final java.lang.String toString()`

### `public java.lang.Boolean trackDelivery()`
