# io.casehub.qhorus.api.spi.CommitmentContext

**Package:** `io.casehub.qhorus.api.spi`

**Kind:** `record`

Contextual identifiers for the commitment being discharged.

<p>
Passed to String, CommitmentContext)
so that implementations can query the ledger or stores before deciding verdict.

<p>
The primary use case is evidential attestation: a policy implementation may call
`EvidentialChecker.checkObligation(terminalType, context)` using the
`correlationId` and `channelId` to verify whether the outcome was honest.
When `artefactUuid` and/or `expectedToken` are populated, V1 (ghost artefact)
and V4 (verification token) evidential checks also fire.

<p>
Refs #304, #342.

## Fields

### `artefactUuid` (`java.util.UUID`)

### `capabilityTag` (`java.lang.String`)

### `channelId` (`java.util.UUID`)

### `channelName` (`java.lang.String`)

### `commitmentId` (`java.util.UUID`)

### `content` (`java.lang.String`)

### `correlationId` (`java.lang.String`)

### `expectedToken` (`java.lang.String`)

## Record Components

### `artefactUuid` (`java.util.UUID`)

UUID of the artefact referenced in the terminal message; may be null.
                     When non-null, V1 ghost-artefact check verifies it exists in the DataStore.

### `capabilityTag` (`java.lang.String`)

extracted from the COMMAND content's `"capability"` JSON field;
                     may be null or `CapabilityTag.GLOBAL` when not available

### `channelId` (`java.util.UUID`)

the channel the commitment was made on

### `channelName` (`java.lang.String`)

for human-readable logging; may be null

### `commitmentId` (`java.util.UUID`)

the specific commitment record; may be null when not tracked

### `content` (`java.lang.String`)

the terminal message content; may be null. Used by V4 for token scanning.

### `correlationId` (`java.lang.String`)

identifies the obligation being discharged

### `expectedToken` (`java.lang.String`)

verification token from the COMMAND content; may be null.
                     When non-null, V4 checks that the terminal message content contains it.

## Constructors

### `public CommitmentContext(java.lang.String correlationId, java.util.UUID channelId, java.lang.String channelName, java.util.UUID commitmentId, java.lang.String capabilityTag, java.util.UUID artefactUuid, java.lang.String expectedToken, java.lang.String content)`

#### Parameters

- `correlationId` (`java.lang.String`)
- `channelId` (`java.util.UUID`)
- `channelName` (`java.lang.String`)
- `commitmentId` (`java.util.UUID`)
- `capabilityTag` (`java.lang.String`)
- `artefactUuid` (`java.util.UUID`)
- `expectedToken` (`java.lang.String`)
- `content` (`java.lang.String`)

## Methods

### `public java.util.UUID artefactUuid()`

### `public java.lang.String capabilityTag()`

### `public java.util.UUID channelId()`

### `public java.lang.String channelName()`

### `public java.util.UUID commitmentId()`

### `public java.lang.String content()`

### `public java.lang.String correlationId()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public java.lang.String expectedToken()`

### `public final int hashCode()`

### `public final java.lang.String toString()`
