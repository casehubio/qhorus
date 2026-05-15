# 0007 — Reactive Stack Activation: @IfBuildProperty + quarkus.datasource.qhorus.reactive

Date: 2026-05-15
Status: Accepted (partially supersedes ADR-0003 — activation mechanism section)

## Context and Problem Statement

ADR-0003 established the dual-stack design and used `casehub.qhorus.reactive.enabled`
(a custom build-time property in `QhorusBuildConfig`) to toggle the reactive stack.
Two problems emerged in casehub-qhorus#141: (1) `quarkus-hibernate-reactive-panache`
was compile-scope, unconditionally activating the Quarkus reactive extension for all
consumers even when `reactive.enabled=false`; and (2) the attempted fix using
`ExcludedTypeBuildItem` + `Capabilities` failed in consumer apps.

## Decision Drivers

* Consumers on JDBC-only datasources must get zero reactive activation with zero
  workaround properties in their configuration
* The selection mechanism must gate both CDI bean registration AND JAX-RS REST
  resource registration — these are processed by separate Quarkus subsystems
* No custom user-facing flag — the activation should be a natural consequence
  of configuration the consumer sets anyway

## Considered Options

* **Option A** — Keep `casehub.qhorus.reactive.enabled` + add `<optional>` to pom
* **Option B** — `ExcludedTypeBuildItem` gated by `Capabilities.isPresent(HIBERNATE_REACTIVE)`
  in `QhorusProcessor` — no flag, classpath-driven
* **Option C** — `@IfBuildProperty`/`@UnlessBuildProperty` tied to
  `quarkus.datasource.qhorus.reactive` + `<optional>` on pom dep (chosen)

## Decision Outcome

Chosen option: **Option C**, because it is the correct Quarkus-native mechanism
for dual-stack REST resource selection, eliminates the custom flag, and uses
the datasource property consumers set anyway.

### Changes from ADR-0003 activation mechanism

| Aspect | ADR-0003 | ADR-0007 |
|---|---|---|
| Dep scope | compile | `<optional>true</optional>` |
| Activation property | `casehub.qhorus.reactive.enabled` | `quarkus.datasource.qhorus.reactive` |
| Config class | `QhorusBuildConfig` | deleted — not needed |
| Processor | `ReactiveEnabled` BooleanSupplier + `ExcludedTypeBuildItem` | `feature()` only |
| Reactive service/store beans | `@Alternative` (no @Priority) | `@Alternative @IfBuildProperty(reactive=true)` |
| Reactive REST/MCP beans | `@IfBuildProperty(reactive.enabled=true)` | `@IfBuildProperty(quarkus.datasource.qhorus.reactive=true)` |

### Positive Consequences

* Consumers on JDBC-only datasources: no dep added, no property set → zero
  reactive extension activation, zero workaround properties needed
* No custom flag to document — `quarkus.datasource.qhorus.reactive=true` is
  the same property the consumer configures for their reactive datasource
* `@IfBuildProperty` gates CDI beans AND JAX-RS resources at augmentation time —
  eliminating the duplicate `/a2a` endpoint issue seen in Claudony

### Negative Consequences / Tradeoffs

* Reactive consumers must explicitly set `quarkus.datasource.qhorus.reactive=true`
  in addition to adding `quarkus-hibernate-reactive-panache` + a reactive driver
* Reactive `@Alternative` service/store beans without `@Priority` require that
  the testing module's `InMemoryReactive*Store @Priority(1)` beans win in test
  consumers — production reactive consumers have the JPA store as the unique
  alternative (auto-selected by Arc)

## Pros and Cons of the Options

### Option A — Keep custom flag + make dep optional

* ✅ Minimal change — only one line in pom.xml changes
* ❌ Custom flag is noise — consumers must know about `casehub.qhorus.reactive.enabled`
  on top of the Quarkus datasource config
* ❌ Two sources of truth: the pom dep presence AND the flag

### Option B — ExcludedTypeBuildItem + Capabilities

* ✅ No user flag — fully classpath-driven
* ❌ `Capabilities` cannot be injected in `BooleanSupplier` — evaluated before
  `CapabilityBuildItem`s are produced by build steps; always returns empty
* ❌ `ExcludedTypeBuildItem` excludes from CDI but NOT from JAX-RS scanner —
  `ResteasyReactiveProcessor` independently registers `@Path` beans; caused
  duplicate `/a2a` endpoint conflict in Claudony (confirmed empirically)
* ❌ `Class.forName()` in `BooleanSupplier` is fragile: evaluates the augmentation
  JVM classpath, not the consumer's runtime classpath, with different results
  across contexts

### Option C — @IfBuildProperty + quarkus.datasource.qhorus.reactive (chosen)

* ✅ Correct Quarkus mechanism: gates CDI AND JAX-RS at augmentation time
* ✅ `@IfBuildProperty` / `@UnlessBuildProperty` JAX-RS bug fixed in 3.2.3.Final
  (Quarkus issues #34938, #16218); verified on 3.32.2
* ✅ Natural property — consumers configure `quarkus.datasource.qhorus.reactive=true`
  for their datasource; no additional flag to learn
* ❌ Reactive service/store beans need `@IfBuildProperty` on 20 additional classes
  (to avoid CDI validation of their injection points in blocking mode)

## Links

* [ADR-0003](0003-reactive-dual-stack.md) — original dual-stack design (dual-stack
  concept unchanged; only activation mechanism superseded)
* casehubio/qhorus#141
* Platform protocol PP-20260514-f41258
* Quarkus issue #34938 — `@UnlessBuildProperty` ignored on generated JAX-RS resources
  (fixed 3.2.3.Final)
* Quarkus issue #16218 — excluded JAX-RS classes visible in OpenAPI (fixed 3.0.0.CR1)
