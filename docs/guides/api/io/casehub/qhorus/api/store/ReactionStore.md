# io.casehub.qhorus.api.store.ReactionStore

**Package:** `io.casehub.qhorus.api.store`

**Kind:** `interface`

## Methods

### `public abstract void deleteByChannel(java.util.UUID channelId)`

#### Parameters

- `channelId` (`java.util.UUID`)

### `public abstract void deleteByMessage(java.lang.Long messageId)`

#### Parameters

- `messageId` (`java.lang.Long`)

### `public abstract io.casehub.qhorus.api.message.Reaction react(java.lang.Long messageId, java.lang.String emoji, java.lang.String actorId, java.lang.String tenancyId)`

#### Parameters

- `messageId` (`java.lang.Long`)
- `emoji` (`java.lang.String`)
- `actorId` (`java.lang.String`)
- `tenancyId` (`java.lang.String`)

### `public abstract boolean unreact(java.lang.Long messageId, java.lang.String emoji, java.lang.String actorId)`

#### Parameters

- `messageId` (`java.lang.Long`)
- `emoji` (`java.lang.String`)
- `actorId` (`java.lang.String`)
