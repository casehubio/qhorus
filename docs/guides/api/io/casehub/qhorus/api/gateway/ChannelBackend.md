# io.casehub.qhorus.api.gateway.ChannelBackend

**Package:** `io.casehub.qhorus.api.gateway`

**Kind:** `interface`

## Methods

### `public abstract ActorType actorType()`

### `public abstract java.lang.String backendId()`

### `public abstract void close(io.casehub.qhorus.api.gateway.ChannelRef channel)`

#### Parameters

- `channel` (`io.casehub.qhorus.api.gateway.ChannelRef`)

### `public default io.casehub.qhorus.api.gateway.DeliveryGuarantee deliveryGuarantee()`

### `public abstract void open(io.casehub.qhorus.api.gateway.ChannelRef channel, java.util.Map<java.lang.String,java.lang.String> metadata)`

#### Parameters

- `channel` (`io.casehub.qhorus.api.gateway.ChannelRef`)
- `metadata` (`java.util.Map<java.lang.String,java.lang.String>`)

### `public abstract void post(io.casehub.qhorus.api.gateway.ChannelRef channel, io.casehub.qhorus.api.gateway.OutboundMessage message)`

See sub-interface for post() exception semantics.

#### Parameters

- `channel` (`io.casehub.qhorus.api.gateway.ChannelRef`)
- `message` (`io.casehub.qhorus.api.gateway.OutboundMessage`)
