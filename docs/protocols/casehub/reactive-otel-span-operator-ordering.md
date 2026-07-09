---
id: PP-20260709-7b9c1b
title: "Chain onFailure before onTermination in reactive OTel span lifecycle"
type: rule
scope: platform
applies_to: "Any Mutiny reactive chain that manages OTel span lifecycle manually"
severity: critical
refs:
  - docs/specs/2026-07-07-otel-trace-instrumentation-design.md
violation_hint: "onTermination().invoke(span.end()) appears before onFailure().invoke(span.setStatus(ERROR)) in a Mutiny chain"
garden_ref: "GE-20260709-16094e"
created: 2026-07-09
---

In Mutiny, operators closer to the upstream fire first during signal propagation.
If `onTermination` is chained before `onFailure`, the span ends before the error
is recorded — and per the OTel API spec, mutations to an ended span are no-ops.
The error is silently lost with no exception or warning. Always chain
`onFailure().invoke()` (to record error status) BEFORE `onTermination().invoke()`
(to close scope and end span).
