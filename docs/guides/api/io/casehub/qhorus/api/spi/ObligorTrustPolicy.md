# io.casehub.qhorus.api.spi.ObligorTrustPolicy

**Package:** `io.casehub.qhorus.api.spi`

**Kind:** `interface`

SPI for evaluating whether an obligor is trusted to fulfil a COMMAND commitment.

<p>Invoked by `MessageService.dispatch()` for COMMAND messages with a named
(non-prefixed) target. Role- and capability-prefixed targets bypass the gate —
there is no specific obligor to evaluate.

<p>The default implementation reads `casehub.qhorus.commitment.min-obligor-trust`
and delegates to `TrustGateService.meetsThreshold()`. Override with
`@Alternative @Priority(1)` to provide capability-scoped or channel-aware
trust evaluation.

<p>Refs #213.

## Methods

### `public abstract boolean permits(io.casehub.qhorus.api.spi.ObligorTrustContext ctx)`

Returns `true` if the obligor identified by `ObligorTrustContext.obligorId()`
is trusted to act as commitment fulfiller for a COMMAND on the given channel.

#### Parameters

- `ctx` (`io.casehub.qhorus.api.spi.ObligorTrustContext`) — context including obligor identity and channel coordinates

#### Returns

`true` to allow the COMMAND; `false` to reject it
