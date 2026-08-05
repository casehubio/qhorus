# io.casehub.qhorus.api.spi.CommitmentAttestationPolicy.AttestationOutcome

**Package:** `io.casehub.qhorus.api.spi`

**Kind:** `record`

Attestation fields to write on the originating COMMAND's `MessageLedgerEntry`.

## Fields

### `attestorId` (`java.lang.String`)

### `attestorType` (`ActorType`)

### `confidence` (`double`)

### `verdict` (`AttestationVerdict`)

## Record Components

### `attestorId` (`java.lang.String`)

the actor making the attestation (sender for DONE, "system" for others)

### `attestorType` (`ActorType`)

AGENT or SYSTEM

### `confidence` (`double`)

strength of evidence in [0.0, 1.0]; the Beta update is weighted by
       `recencyWeight x confidence` — higher values move the score more

### `verdict` (`AttestationVerdict`)

SOUND for positive outcomes, FLAGGED for negative; feeds the
       Bayesian Beta trust score in casehub-ledger

## Constructors

### `public AttestationOutcome(AttestationVerdict verdict, double confidence, java.lang.String attestorId, ActorType attestorType)`

#### Parameters

- `verdict` (`AttestationVerdict`)
- `confidence` (`double`)
- `attestorId` (`java.lang.String`)
- `attestorType` (`ActorType`)

## Methods

### `public java.lang.String attestorId()`

### `public ActorType attestorType()`

### `public double confidence()`

### `public final boolean equals(java.lang.Object o)`

#### Parameters

- `o` (`java.lang.Object`)

### `public final int hashCode()`

### `public final java.lang.String toString()`

### `public AttestationVerdict verdict()`
