# 0011 — WatchdogAlertRouter: SPI with config-backed @DefaultBean

Date: 2026-05-27
Status: Accepted

## Context and Problem Statement

`ConnectorAlertBridge` observes `WatchdogAlertEvent` and must route each alert to one or more
connectors (Slack, email, SMS) at configured destinations. The routing strategy — which
connectors receive which alerts, and at what destination addresses — must be expressible
without code changes for simple deployments while remaining extensible for applications with
per-watchdog or severity-based routing logic.

## Decision Drivers

* `ConnectorMessage.destination` is per-message (webhook URL, phone, email address); the bridge
  cannot derive it from the event payload alone
* Simple deployments need zero-code setup: configure endpoints in `application.properties`
* Sophisticated consumers (Claudony, devtown) need per-watchdog routing without forking the bridge
* `casehub-connectors` consolidation (parent#5) is unresolved — the routing abstraction must be
  independent of the current connector SPI's final shape
* Pattern must be consistent with existing qhorus SPIs (`CommitmentAttestationPolicy`,
  `InstanceActorIdProvider`)

## Considered Options

* **Option A** — Global config list: `casehub.qhorus.watchdog.alert.endpoints[*]` fan-out to all watchdogs
* **Option B** — Schema extension: per-watchdog `alertEndpoints` column in the `watchdog` table
* **Option C** — SPI (`WatchdogAlertRouter`) with `ConfiguredWatchdogAlertRouter @DefaultBean` implementing Option A semantics

## Decision Outcome

Chosen option: **Option C — SPI with A-backed @DefaultBean**, because it provides the
out-of-the-box usability of global config (Option A) while making per-watchdog and
severity-based routing possible without modifying the bridge. The `@DefaultBean` pattern is
already established in qhorus for `CommitmentAttestationPolicy` and `InstanceActorIdProvider`.
Consuming apps override by declaring any non-`@DefaultBean` `WatchdogAlertRouter` bean.

### Positive Consequences

* Zero-code deployment: set `casehub.qhorus.watchdog.alert.endpoints[*]` in config
* Arbitrary routing logic: apps provide `@ApplicationScoped WatchdogAlertRouter` to displace the default
* No schema migration: routing config stays in `application.properties`, not the domain schema
* `WatchdogAlertRouter` returns `List<AlertDeliveryTarget>` (qhorus-api type) — the bridge builds `ConnectorMessage`; `casehub-qhorus-api` stays free of any `casehub-connectors` dependency

### Negative Consequences / Tradeoffs

* `ConfiguredWatchdogAlertRouter` ignores the event entirely — all alerts fan-out to all configured endpoints regardless of condition type; per-type routing requires a custom implementation
* `@WithDefault("")` on `List<AlertEndpoint> endpoints()` is required by SmallRye Config or startup fails with SRCFG00014 when no endpoints are configured

## Pros and Cons of the Options

### Option A — Global config list

* ✅ Zero code; operator-only setup
* ❌ No per-watchdog discrimination — SLA alerts and health-check alerts reach the same endpoints
* ❌ Becomes a policy-in-disguise as config entries multiply

### Option B — Schema extension

* ✅ Per-watchdog routing without custom code
* ❌ Deployment config (webhook URLs, phone numbers) belongs in application config, not domain schema
* ❌ Forces a Flyway V11 migration on every deployment, even those never using the bridge

### Option C — SPI with A-backed @DefaultBean

* ✅ Simple deployments work with config only
* ✅ Sophisticated consumers override cleanly without touching bridge code
* ✅ Consistent with CommitmentAttestationPolicy and InstanceActorIdProvider patterns
* ❌ Default implementation is a blunt fan-out; callers must know to override for discrimination

## Links

* casehubio/qhorus#200 — implementation issue
* casehubio/parent#5 — connector SPI consolidation (does not block this decision)
* `api/src/main/java/io/casehub/qhorus/api/watchdog/WatchdogAlertRouter.java`
* `runtime/src/main/java/io/casehub/qhorus/runtime/watchdog/ConfiguredWatchdogAlertRouter.java`
