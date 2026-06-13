---
id: PP-20260613-bf76a2
title: "A2A task state for DECLINE must be 'cancelled', not 'failed'"
type: rule
scope: repo
applies_to: "runtime/api/A2ATaskState — all A2A task state derivation paths (fromCommitmentState, fromMessageHistory, fromMessageType)"
severity: important
refs:
  - runtime/src/main/java/io/casehub/qhorus/runtime/api/A2ATaskState.java
violation_hint: "fromCommitmentState(DECLINED) returning 'failed', or priority system treating DECLINE at the same level as FAILURE — the A2A protocol distinguishes 'cancelled' (explicit refusal) from 'failed' (infrastructure error)"
created: 2026-06-13
---

The A2A protocol defines four task terminal states: `completed`, `failed`, `cancelled`, and
`input_required`. A qhorus DECLINE is an explicit agent refusal — the agent is saying "I
will not do this." This maps to A2A `"cancelled"`. A FAILURE is an infrastructure or
execution error — it maps to `"failed"`. All three `A2ATaskState` derivation methods must
respect this distinction: `fromCommitmentState(DECLINED)` → `"cancelled"`,
`fromMessageHistory` with DECLINE at priority 2 (below FAILURE at 3) → `"cancelled"` when
DECLINE is the highest-priority message, and `fromMessageType(DECLINE)` → `"cancelled"`.
Mapping DECLINE to `"failed"` is semantically wrong and misleads external A2A orchestrators
about why a task did not complete.
