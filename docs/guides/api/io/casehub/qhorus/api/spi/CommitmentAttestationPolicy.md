# io.casehub.qhorus.api.spi.CommitmentAttestationPolicy

**Package:** `io.casehub.qhorus.api.spi`

**Kind:** `interface`

Determines what attestation to write when a message discharges a commitment.

<p>
Returning `Optional.empty()` suppresses attestation writing for that message type.

<p>
Refs #123, #304.

## Methods

### `public abstract java.util.Optional<io.casehub.qhorus.api.spi.CommitmentAttestationPolicy.AttestationOutcome> attestationFor(io.casehub.qhorus.api.message.MessageType terminalType, java.lang.String resolvedActorId, io.casehub.qhorus.api.spi.CommitmentContext context)`

Determine what attestation to write for a commitment outcome.

#### Parameters

- `terminalType` (`io.casehub.qhorus.api.message.MessageType`) — the message type that discharged the commitment
- `resolvedActorId` (`java.lang.String`) — the ledger actorId of the message sender, already resolved
       through `InstanceActorIdProvider`
- `context` (`io.casehub.qhorus.api.spi.CommitmentContext`) — commitment identifiers (corrId, channelId, capabilityTag) available for
       evidential checks; may be null in some callers — implementations must handle null defensively

#### Returns

the attestation to write, or empty to write no attestation
