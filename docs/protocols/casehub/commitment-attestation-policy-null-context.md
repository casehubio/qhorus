---
id: PP-20260623-77adf0
title: "CommitmentAttestationPolicy implementations must handle null CommitmentContext defensively"
type: rule
scope: platform
applies_to: "Any implementation of CommitmentAttestationPolicy in any casehub module"
severity: important
refs:
  - api/src/main/java/io/casehub/qhorus/api/spi/CommitmentAttestationPolicy.java
violation_hint: "NPE thrown in production when a legacy caller (e.g. unit test, 2-arg lambda) invokes attestationFor(type, actorId) — the 2-arg default delegates to the 3-arg method with context=null"
created: 2026-06-23
---

The `CommitmentAttestationPolicy` interface exposes both a 3-arg abstract method `attestationFor(MessageType, String, CommitmentContext)` and a 2-arg default method that delegates with `context=null`. Any implementation that dereferences `context` without a null guard will throw `NullPointerException` when invoked from a 2-arg caller. Guard every context access: `if (context != null) { ... }`. This includes casehub-devtown implementations that use `EvidentialChecker.checkObligation(terminalType, context)` — pass the nullable `context` through; `EvidentialChecker` accepts null and treats it as "no extended context available".
