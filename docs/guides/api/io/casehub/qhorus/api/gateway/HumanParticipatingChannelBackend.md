# io.casehub.qhorus.api.gateway.HumanParticipatingChannelBackend

**Package:** `io.casehub.qhorus.api.gateway`

**Kind:** `interface`

At most one per channel. Full speech act inbound via `InboundNormaliser`.
`actorType()` must return `ActorType.HUMAN`.
Call `gateway.receiveHumanMessage()` when inbound arrives.

<p>`post()` must catch all exceptions internally — failure is non-fatal;
the gateway logs and continues.

<p>Override `.normaliserFor(UUID)` to provide per-channel type inference.
Return `null` (the default) to use the system `DefaultInboundNormaliser`.

## Methods

### `public default io.casehub.qhorus.api.gateway.InboundNormaliser normaliserFor(java.util.UUID channelId)`

Returns the `InboundNormaliser` for messages received from this backend
on the given channel, or `null` to use the system default normaliser.

<p>The normaliser converts raw prose input (an `InboundHumanMessage`) into
a typed `NormalisedMessage`. Backends that know the message type and
reply context (e.g. a UI with explicit Reply / New Message controls) should
override this and return a normaliser that reads from the message's fields
rather than inferring from metadata.

<p><strong>Parameter is UUID, not ChannelRef:</strong> This method is called
during backend registration before the full Channel entity is available. Using
UUID avoids a circular dependency where registration needs the normaliser before
the channel is fully initialized.

#### Parameters

- `channelId` (`java.util.UUID`) — the channel UUID

#### Returns

normaliser for this channel, or `null` for default
