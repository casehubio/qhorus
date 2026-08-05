# io.casehub.qhorus.api.gateway.HumanObserverChannelBackend

**Package:** `io.casehub.qhorus.api.gateway`

**Kind:** `interface`

Unlimited per channel. Inbound capped to `EVENT` by gateway regardless of content.
`actorType()` must return `ActorType.HUMAN`.
Call `gateway.receiveObserverSignal()` when inbound arrives.

<p>`post()` must catch all exceptions internally — failure is non-fatal;
the gateway logs and continues.
