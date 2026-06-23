---
id: PP-20260623-fd69f3
title: "Verify COMMAND obligation outcomes by response-type inspection, not CommitmentStore state"
type: rule
scope: platform
applies_to: "Any evidential verification, audit checker, or Zone 3 implementation built on casehub-qhorus"
severity: important
refs:
  - examples/agent-communication/src/main/java/io/casehub/qhorus/examples/benchmark/EvidentialChecker.java
violation_hint: "EvidentialChecker checks commitmentStore.findByCorrelationId(corrId).state == OPEN but finds FULFILLED — silently misses wrong-type (RESPONSE/QUERY) responses to COMMAND obligations"
garden_ref: "GE-20260623-92964b"
created: 2026-06-23
---

When building verification tooling that checks whether a COMMAND obligation was correctly resolved, check the **response type** (was it DONE, FAILURE, or DECLINE?), not the **CommitmentStore state** (was it OPEN?). RESPONSE sent with a COMMAND's correlationId causes `CommitmentService.fulfill()` to fire — the commitment transitions to `FULFILLED`, not `OPEN`. This is semantically wrong (RESPONSE is query-fulfillment vocabulary), but the CommitmentStore does not distinguish COMMAND vs QUERY obligations — it fulfills on any matching correlationId. A verifier that checks `state == OPEN` will silently pass RESPONSE (commitment is FULFILLED, no violation detected) when it should flag I_ec (wrong terminal type for COMMAND obligation). The correct guard is: `if (type not in {DONE, FAILURE, DECLINE}) → I_ec violation`.
