# io.casehub.qhorus.api.gateway.AgentChannelBackend

**Package:** `io.casehub.qhorus.api.gateway`

**Kind:** `interface`

Always registered. Internal Qhorus agent mesh. `actorType()` must return
`ActorType.AGENT`.

<p>`post()` may throw — it is the source-of-truth write; the gateway
treats failure as fatal and surfaces the error to the caller.
