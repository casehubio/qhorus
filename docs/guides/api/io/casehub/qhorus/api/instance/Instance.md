# io.casehub.qhorus.api.instance.Instance

**Package:** `io.casehub.qhorus.api.instance`

**Kind:** `record`

## Fields

### `claudonySessionId` (`java.lang.String`)

### `description` (`java.lang.String`)

### `id` (`java.util.UUID`)

### `instanceId` (`java.lang.String`)

### `lastSeen` (`java.time.Instant`)

### `readOnly` (`boolean`)

### `registeredAt` (`java.time.Instant`)

### `sessionToken` (`java.lang.String`)

### `status` (`java.lang.String`)

## Record Components

### `claudonySessionId` (`java.lang.String`)

### `description` (`java.lang.String`)

### `id` (`java.util.UUID`)

### `instanceId` (`java.lang.String`)

### `lastSeen` (`java.time.Instant`)

### `readOnly` (`boolean`)

### `registeredAt` (`java.time.Instant`)

### `sessionToken` (`java.lang.String`)

### `status` (`java.lang.String`)

## Constructors

### `public Instance(java.util.UUID id, java.lang.String instanceId, java.lang.String description, java.lang.String status, java.lang.String claudonySessionId, java.lang.String sessionToken, boolean readOnly, java.time.Instant lastSeen, java.time.Instant registeredAt)`

#### Parameters

- `id` (`java.util.UUID`)
- `instanceId` (`java.lang.String`)
- `description` (`java.lang.String`)
- `status` (`java.lang.String`)
- `claudonySessionId` (`java.lang.String`)
- `sessionToken` (`java.lang.String`)
- `readOnly` (`boolean`)
- `lastSeen` (`java.time.Instant`)
- `registeredAt` (`java.time.Instant`)

## Methods

### `public static io.casehub.qhorus.api.instance.Instance.Builder builder(java.lang.String instanceId)`

#### Parameters

- `instanceId` (`java.lang.String`)

### `public java.lang.String claudonySessionId()`

### `public java.lang.String description()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.util.UUID id()`

### `public java.lang.String instanceId()`

### `public java.time.Instant lastSeen()`

### `public boolean readOnly()`

### `public java.time.Instant registeredAt()`

### `public java.lang.String sessionToken()`

### `public java.lang.String status()`

### `public io.casehub.qhorus.api.instance.Instance.Builder toBuilder()`

### `public final java.lang.String toString()`
