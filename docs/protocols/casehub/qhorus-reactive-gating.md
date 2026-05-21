---
id: PP-20260520-a86db7
title: "qhorus reactive stack gating uses @IfBuildProperty per-bean, not ExcludedTypeBuildItem"
type: rule
scope: repo
applies_to: "casehub-qhorus runtime reactive beans and deployment module QhorusProcessor"
severity: important
refs:
  - docs/protocols/casehub/reactive-service-build-gating.md
violation_hint: "Duplicate @Tool method errors at augmentation time — both QhorusMcpTools and ReactiveQhorusMcpTools active simultaneously"
created: 2026-05-20
---

qhorus diverges from PP-20260519-39a9a5: `ExcludedTypeBuildItem` produced by a `@BuildStep` in `QhorusProcessor` is silently not invoked during Quarkus 3.32.2 workspace test augmentation (method is present in bytecode and `quarkus-build-steps.list`, but the framework does not call it). The working mechanism is `@IfBuildProperty(name = "casehub.qhorus.reactive.enabled", stringValue = "true")` per reactive bean, with `QhorusBuildTimeConfig` (`@ConfigRoot(phase = BUILD_TIME)`) in the deployment module formally declaring the property so `@IfBuildProperty` evaluates reliably. `casehub.qhorus.reactive.enabled` must not appear in `application.properties` — its presence causes `SRCFG00050` SmallRye Config validation errors at runtime (BUILD_TIME properties have no runtime `@ConfigRoot` mapping).
