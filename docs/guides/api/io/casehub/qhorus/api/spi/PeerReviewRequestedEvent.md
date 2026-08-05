# io.casehub.qhorus.api.spi.PeerReviewRequestedEvent

**Package:** `io.casehub.qhorus.api.spi`

**Kind:** `record`

## Fields

### `channelId` (`java.util.UUID`)

### `ledgerEntryId` (`java.util.UUID`)

### `tenancyId` (`java.lang.String`)

## Record Components

### `channelId` (`java.util.UUID`)

### `ledgerEntryId` (`java.util.UUID`)

### `tenancyId` (`java.lang.String`)

## Constructors

### `public PeerReviewRequestedEvent(java.util.UUID ledgerEntryId, java.util.UUID channelId, java.lang.String tenancyId)`

#### Parameters

- `ledgerEntryId` (`java.util.UUID`)
- `channelId` (`java.util.UUID`)
- `tenancyId` (`java.lang.String`)

## Methods

### `public java.util.UUID channelId()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public java.util.UUID ledgerEntryId()`

### `public java.lang.String tenancyId()`

### `public final java.lang.String toString()`
