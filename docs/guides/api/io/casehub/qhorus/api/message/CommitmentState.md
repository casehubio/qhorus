# io.casehub.qhorus.api.message.CommitmentState

**Package:** `io.casehub.qhorus.api.message`

**Kind:** `enum`

Obligation lifecycle state for a QUERY or COMMAND commitment.

## Enum Constants

### `ACKNOWLEDGED` (`io.casehub.qhorus.api.message.CommitmentState`)

STATUS received; debtor is working and has extended their deadline.

### `DECLINED` (`io.casehub.qhorus.api.message.CommitmentState`)

DECLINE received; debtor refused the obligation.

### `DELEGATED` (`io.casehub.qhorus.api.message.CommitmentState`)

HANDOFF received; obligation transferred to a new debtor. A child Commitment was created.

<p><strong>Terminal state.</strong> This commitment is closed. The active obligation lives
in the child Commitment (state `OPEN`), not here.
Use `CommitmentStore.findByCorrelationId()` to locate the child — it returns the
child OPEN commitment after a HANDOFF, not this DELEGATED parent.

<p><strong>Cross-system warning:</strong> `WorkItemStatus.DELEGATED` in
`casehub-work` (refs casehubio/work#240) is <em>non-terminal</em> —
it represents a pre-acceptance hold, not a closed obligation.
Do not conflate the two when writing integration code that crosses both systems.

### `EXPIRED` (`io.casehub.qhorus.api.message.CommitmentState`)

Deadline exceeded with no response; infrastructure-generated terminal state.

### `FAILED` (`io.casehub.qhorus.api.message.CommitmentState`)

FAILURE received; debtor attempted but could not complete.

### `FULFILLED` (`io.casehub.qhorus.api.message.CommitmentState`)

RESPONSE (for QUERY) or DONE (for COMMAND) received; obligation discharged.

### `OPEN` (`io.casehub.qhorus.api.message.CommitmentState`)

QUERY or COMMAND sent; debtor must respond or decline.

## Constructors

### `private CommitmentState()`

## Methods

### `public boolean isActive()`

True for states where the obligation is still in flight.

### `public boolean isTerminal()`

True for all states from which no further transition is possible.

### `public static io.casehub.qhorus.api.message.CommitmentState valueOf(java.lang.String name)`

#### Parameters

- `name` (`java.lang.String`)

### `public static io.casehub.qhorus.api.message.CommitmentState[] values()`
