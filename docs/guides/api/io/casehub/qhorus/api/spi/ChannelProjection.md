# io.casehub.qhorus.api.spi.ChannelProjection

**Package:** `io.casehub.qhorus.api.spi`

**Kind:** `interface`

A pure left-fold over a channel's message history.

<p>Implement this SPI to derive a deterministic read-model (`S`) from a
sequence of typed messages. `ProjectionService` reads the channel's message
history, folds it step-by-step via `.apply`, and returns a
`ProjectionResult` containing the materialised state and a cursor.

<p><strong>Contract — implementors must honour all of these:</strong>
<ul>
  <li>`.identity()` must return a <em>fresh</em> instance on every call.
      If `S` is mutable (e.g. a `HashMap` accumulator), returning a
      cached singleton creates shared state across concurrent `project()` calls
      and will produce incorrect results.</li>
  <li>`.apply` must be <em>pure</em>: no external state reads or writes,
      no side effects, no thread-local access. Return `state` unchanged for
      messages this projection does not handle — do not return `null`.</li>
  <li>`.apply` must not throw. If it does (unchecked), the exception propagates
      from `project()` without partial-state recovery.</li>
</ul>

<p><strong>Rendering:</strong> `ProjectionService` returns the typed state `S`.
Consumers convert `S` to an output format (markdown, JSON, etc.) themselves —
for example with a `Function<S, String>` or a purpose-built renderer.
The service never calls a render method; rendering is a consumer-side concern.

<p><strong>Registries — two planned models, orthogonal:</strong>
<ul>
  <li><em>Explicit selection</em> (`RenderableProjection` + `ProjectionRegistry`,
      current): implement `RenderableProjection` and declare
      `RenderableProjection.projectionName()` — the tool caller names the projection
      explicitly via `project_channel("my-channel", "summary")`.</li>
  <li><em>Automatic routing</em> (`@ChannelBound`, future): a channel name routes to a
      projection automatically without tool-caller involvement — designed for dashboards and
      automated read-models. A `RenderableProjection` bean can carry `@ChannelBound`
      as well; the two mechanisms are orthogonal qualifiers on the same bean.</li>
</ul>

## Methods

### `public abstract S apply(S state, io.casehub.qhorus.api.message.MessageView message)`

Pure fold step: given the current accumulated `state` and the next
`message`, return the next state.

<p>Return `state` unchanged for messages this projection ignores.
Never return `null`.

#### Parameters

- `state` (`S`) — current accumulated state — never `null`
- `message` (`io.casehub.qhorus.api.message.MessageView`) — the next message to fold — never `null`

#### Returns

the updated state — must not be `null`

### `public abstract S identity()`

Returns the neutral element — the empty initial state before any messages are folded.

<p>Called once per `project()` invocation. Must return a fresh instance.
