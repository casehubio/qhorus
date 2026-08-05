# io.casehub.qhorus.api.channel.PresenceTracker

**Package:** `io.casehub.qhorus.api.channel`

**Kind:** `interface`

## Methods

### `public abstract java.util.List<io.casehub.qhorus.api.channel.Presence> getChannelPresence(java.util.UUID channelId)`

#### Parameters

- `channelId` (`java.util.UUID`)

### `public abstract io.casehub.qhorus.api.channel.Presence getPresence(java.lang.String memberId)`

#### Parameters

- `memberId` (`java.lang.String`)

### `public abstract void heartbeat(io.casehub.qhorus.api.channel.PresenceStatus status, java.lang.String statusMessage)`

#### Parameters

- `status` (`io.casehub.qhorus.api.channel.PresenceStatus`)
- `statusMessage` (`java.lang.String`)

### `public abstract void setOffline()`
