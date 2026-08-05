# io.casehub.qhorus.api.channel.ChannelCreateRequest

**Package:** `io.casehub.qhorus.api.channel`

**Kind:** `record`

## Fields

### `adminInstances` (`java.util.List<java.lang.String>`)

### `allowedTypes` (`java.util.Set<io.casehub.qhorus.api.message.MessageType>`)

### `allowedWriters` (`java.util.List<java.lang.String>`)

### `barrierContributors` (`java.util.List<java.lang.String>`)

### `deniedTypes` (`java.util.Set<io.casehub.qhorus.api.message.MessageType>`)

### `description` (`java.lang.String`)

### `externalKey` (`java.lang.String`)

### `inboundConnectorId` (`java.lang.String`)

### `name` (`java.lang.String`)

### `outboundConnectorId` (`java.lang.String`)

### `outboundDestination` (`java.lang.String`)

### `protocolParticipants` (`java.util.List<java.lang.String>`)

### `protocols` (`java.util.List<java.lang.String>`)

### `rateLimitPerChannel` (`java.lang.Integer`)

### `rateLimitPerInstance` (`java.lang.Integer`)

### `reviewerInstances` (`java.util.List<java.lang.String>`)

### `semantic` (`io.casehub.qhorus.api.channel.ChannelSemantic`)

### `spaceId` (`java.util.UUID`)

### `trackDelivery` (`java.lang.Boolean`)

## Record Components

### `adminInstances` (`java.util.List<java.lang.String>`)

### `allowedTypes` (`java.util.Set<io.casehub.qhorus.api.message.MessageType>`)

### `allowedWriters` (`java.util.List<java.lang.String>`)

### `barrierContributors` (`java.util.List<java.lang.String>`)

### `deniedTypes` (`java.util.Set<io.casehub.qhorus.api.message.MessageType>`)

### `description` (`java.lang.String`)

### `externalKey` (`java.lang.String`)

### `inboundConnectorId` (`java.lang.String`)

### `name` (`java.lang.String`)

### `outboundConnectorId` (`java.lang.String`)

### `outboundDestination` (`java.lang.String`)

### `protocolParticipants` (`java.util.List<java.lang.String>`)

### `protocols` (`java.util.List<java.lang.String>`)

### `rateLimitPerChannel` (`java.lang.Integer`)

### `rateLimitPerInstance` (`java.lang.Integer`)

### `reviewerInstances` (`java.util.List<java.lang.String>`)

### `semantic` (`io.casehub.qhorus.api.channel.ChannelSemantic`)

### `spaceId` (`java.util.UUID`)

### `trackDelivery` (`java.lang.Boolean`)

## Constructors

### `public ChannelCreateRequest(java.lang.String name, java.lang.String description, io.casehub.qhorus.api.channel.ChannelSemantic semantic, java.util.List<java.lang.String> barrierContributors, java.util.List<java.lang.String> allowedWriters, java.util.List<java.lang.String> adminInstances, java.lang.Integer rateLimitPerChannel, java.lang.Integer rateLimitPerInstance, java.util.Set<io.casehub.qhorus.api.message.MessageType> allowedTypes, java.util.Set<io.casehub.qhorus.api.message.MessageType> deniedTypes, java.lang.String inboundConnectorId, java.lang.String externalKey, java.lang.String outboundConnectorId, java.lang.String outboundDestination)`

#### Parameters

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
- `inboundConnectorId` (`java.lang.String`)
- `externalKey` (`java.lang.String`)
- `outboundConnectorId` (`java.lang.String`)
- `outboundDestination` (`java.lang.String`)

### `public ChannelCreateRequest(java.lang.String name, java.lang.String description, io.casehub.qhorus.api.channel.ChannelSemantic semantic, java.util.List<java.lang.String> barrierContributors, java.util.List<java.lang.String> allowedWriters, java.util.List<java.lang.String> adminInstances, java.lang.Integer rateLimitPerChannel, java.lang.Integer rateLimitPerInstance, java.util.Set<io.casehub.qhorus.api.message.MessageType> allowedTypes, java.util.Set<io.casehub.qhorus.api.message.MessageType> deniedTypes, java.util.UUID spaceId, java.lang.String inboundConnectorId, java.lang.String externalKey, java.lang.String outboundConnectorId, java.lang.String outboundDestination)`

#### Parameters

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
- `spaceId` (`java.util.UUID`)
- `inboundConnectorId` (`java.lang.String`)
- `externalKey` (`java.lang.String`)
- `outboundConnectorId` (`java.lang.String`)
- `outboundDestination` (`java.lang.String`)

### `public ChannelCreateRequest(java.lang.String name, java.lang.String description, io.casehub.qhorus.api.channel.ChannelSemantic semantic, java.util.List<java.lang.String> barrierContributors, java.util.List<java.lang.String> allowedWriters, java.util.List<java.lang.String> adminInstances, java.lang.Integer rateLimitPerChannel, java.lang.Integer rateLimitPerInstance, java.util.Set<io.casehub.qhorus.api.message.MessageType> allowedTypes, java.util.Set<io.casehub.qhorus.api.message.MessageType> deniedTypes, java.util.UUID spaceId, java.util.List<java.lang.String> reviewerInstances, java.lang.String inboundConnectorId, java.lang.String externalKey, java.lang.String outboundConnectorId, java.lang.String outboundDestination)`

#### Parameters

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
- `spaceId` (`java.util.UUID`)
- `reviewerInstances` (`java.util.List<java.lang.String>`)
- `inboundConnectorId` (`java.lang.String`)
- `externalKey` (`java.lang.String`)
- `outboundConnectorId` (`java.lang.String`)
- `outboundDestination` (`java.lang.String`)

### `public ChannelCreateRequest(java.lang.String name, java.lang.String description, io.casehub.qhorus.api.channel.ChannelSemantic semantic, java.util.List<java.lang.String> barrierContributors, java.util.List<java.lang.String> allowedWriters, java.util.List<java.lang.String> adminInstances, java.lang.Integer rateLimitPerChannel, java.lang.Integer rateLimitPerInstance, java.util.Set<io.casehub.qhorus.api.message.MessageType> allowedTypes, java.util.Set<io.casehub.qhorus.api.message.MessageType> deniedTypes, java.util.UUID spaceId, java.util.List<java.lang.String> reviewerInstances, java.util.List<java.lang.String> protocols, java.util.List<java.lang.String> protocolParticipants, java.lang.Boolean trackDelivery, java.lang.String inboundConnectorId, java.lang.String externalKey, java.lang.String outboundConnectorId, java.lang.String outboundDestination)`

#### Parameters

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
- `spaceId` (`java.util.UUID`)
- `reviewerInstances` (`java.util.List<java.lang.String>`)
- `protocols` (`java.util.List<java.lang.String>`)
- `protocolParticipants` (`java.util.List<java.lang.String>`)
- `trackDelivery` (`java.lang.Boolean`)
- `inboundConnectorId` (`java.lang.String`)
- `externalKey` (`java.lang.String`)
- `outboundConnectorId` (`java.lang.String`)
- `outboundDestination` (`java.lang.String`)

### `public ChannelCreateRequest(java.lang.String name, java.lang.String description, io.casehub.qhorus.api.channel.ChannelSemantic semantic, java.util.List<java.lang.String> barrierContributors, java.util.List<java.lang.String> allowedWriters, java.util.List<java.lang.String> adminInstances, java.lang.Integer rateLimitPerChannel, java.lang.Integer rateLimitPerInstance, java.util.Set<io.casehub.qhorus.api.message.MessageType> allowedTypes, java.util.Set<io.casehub.qhorus.api.message.MessageType> deniedTypes, java.util.UUID spaceId, java.util.List<java.lang.String> reviewerInstances, java.util.List<java.lang.String> protocols, java.util.List<java.lang.String> protocolParticipants, java.lang.String inboundConnectorId, java.lang.String externalKey, java.lang.String outboundConnectorId, java.lang.String outboundDestination)`

#### Parameters

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
- `spaceId` (`java.util.UUID`)
- `reviewerInstances` (`java.util.List<java.lang.String>`)
- `protocols` (`java.util.List<java.lang.String>`)
- `protocolParticipants` (`java.util.List<java.lang.String>`)
- `inboundConnectorId` (`java.lang.String`)
- `externalKey` (`java.lang.String`)
- `outboundConnectorId` (`java.lang.String`)
- `outboundDestination` (`java.lang.String`)

## Methods

### `public java.util.List<java.lang.String> adminInstances()`

### `public java.util.Set<io.casehub.qhorus.api.message.MessageType> allowedTypes()`

### `public java.util.List<java.lang.String> allowedWriters()`

### `public java.util.List<java.lang.String> barrierContributors()`

### `public static io.casehub.qhorus.api.channel.ChannelCreateRequest.Builder builder(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public java.util.Set<io.casehub.qhorus.api.message.MessageType> deniedTypes()`

### `public java.lang.String description()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public java.lang.String externalKey()`

### `public boolean hasConnectorBinding()`

### `public final int hashCode()`

### `public java.lang.String inboundConnectorId()`

### `public java.lang.String name()`

### `public java.lang.String outboundConnectorId()`

### `public java.lang.String outboundDestination()`

### `public java.util.List<java.lang.String> protocolParticipants()`

### `public java.util.List<java.lang.String> protocols()`

### `public java.lang.Integer rateLimitPerChannel()`

### `public java.lang.Integer rateLimitPerInstance()`

### `public java.util.List<java.lang.String> reviewerInstances()`

### `public io.casehub.qhorus.api.channel.ChannelSemantic semantic()`

### `public java.util.UUID spaceId()`

### `public final java.lang.String toString()`

### `public java.lang.Boolean trackDelivery()`
