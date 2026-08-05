# io.casehub.qhorus.api.spi.RenderableProjection

**Package:** `io.casehub.qhorus.api.spi`

**Kind:** `interface`

A `ChannelProjection` that can render its materialised state as a String.

<p>Extend this interface to register a projection with `ProjectionRegistry`
for use via the `project_channel` MCP tool. Implement all three methods:
`.projectionName()` for registry lookup, the inherited fold methods from
`ChannelProjection`, and `.render(ProjectionResult)` for the tool output.

<p><strong>Registry:</strong> `ProjectionRegistry` (in the runtime module)
collects all CDI beans implementing this interface at startup, indexed by
`.projectionName()`. Names must be unique across all registered beans;
a duplicate is detected at deployment time and fails fast.

<p><strong>Registries — two models, orthogonal:</strong>
<ul>
  <li><em>Explicit selection</em> (this interface): the tool caller names the
      projection — `project_channel("my-channel", "summary")`. The
      `ProjectionRegistry` selects by `.projectionName()`.</li>
  <li><em>Automatic routing</em> (future `@ChannelBound`): the channel name
      routes to a projection automatically, without tool-caller involvement.
      Designed for dashboards and automated read-models.
      A `RenderableProjection` bean can also carry `@ChannelBound`
      — the two mechanisms are orthogonal qualifiers on the same bean.</li>
</ul>

<p><strong>Multi-format rendering:</strong> to render the same projection in
multiple formats, create separate beans with distinct `.projectionName()`
values (e.g. `"summary-markdown"` and `"summary-json"`) and share the
fold logic via delegation. Do not use a format parameter on `.render`.

<p><strong>CDI scope:</strong> `@ApplicationScoped` is recommended. Beans
must be stateless — fold state lives in `ProjectionResult`, not in the bean.
`@Dependent` is permitted; the registry holds the reference for the
application lifetime, which is the effective scope.

<p><strong>Contract:</strong>
<ul>
  <li>`.projectionName()` must return a non-null, non-empty, stable identifier.</li>
  <li>`.render(ProjectionResult)` must return a non-null, non-empty String,
      including when `result.isEmpty() == true` (empty channel). Use
      `ProjectionResult.isEmpty()` rather than checking whether `state`
      equals `identity()` — they are not equivalent (e.g. a COMMAND counter
      on a channel with only EVENTs also produces `identity()` but is not empty).</li>
  <li>`.render(ProjectionResult)` must be pure and non-blocking — called on
      the MCP dispatch thread. Must not throw — unchecked exceptions propagate
      from `project_channel`.</li>
</ul>

<p>Refs qhorus#232.

## Methods

### `public abstract java.lang.String projectionName()`

The name under which this projection is registered in `ProjectionRegistry`.

<p>Must be unique across all `RenderableProjection` beans in the CDI
context. A duplicate detected at startup fails with `IllegalStateException`.
Use a stable, meaningful identifier — callers reference this from MCP tool arguments.

#### Returns

non-null, non-empty projection name

### `public abstract java.lang.String render(io.casehub.qhorus.api.spi.ProjectionResult<S> result)`

Converts the fold result to a String suitable for return from the
`project_channel` MCP tool.

<p>The full `ProjectionResult` is passed rather than just the state
because `state == identity()` is ambiguous: it may mean the channel is
empty, or that the fold produced no output for this projection's criteria
(e.g. a COMMAND counter on a channel with only EVENTs). Only
`ProjectionResult.isEmpty()` gives the definitive empty-channel signal.

#### Parameters

- `result` (`io.casehub.qhorus.api.spi.ProjectionResult<S>`) — the completed fold result — never `null`

#### Returns

a non-null, non-empty String; a human-readable "empty" message
        when `result.isEmpty() == true`
