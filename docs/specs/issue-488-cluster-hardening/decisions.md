# Decisions — #488 Cluster Hardening

## D1: Cross-node dispatch E2E test approach

**Choice:** Add a test to `DispatchRoutingE2ETest` that sends from node-b to a channel created on node-a, verifying the message arrives in the shared database via the `WriteRoutingDecorator` → `WriteProxyClient` → `InternalMeshResource` proxy chain.
**Alternatives:**
- Separate test class — unnecessary; the test fits naturally alongside existing routing tests
- Mock-based unit test — wouldn't validate the real container-to-container proxy path
**Rationale:** The existing E2E tests only send from node-a. Sending from node-b exercises the cross-node proxy path that #486/#487 fixed, providing end-to-end regression coverage.
**Trade-offs:** None significant — XS effort, validates the fix that already landed.
**Sources:** `e2e-cluster/DispatchRoutingE2ETest.java`, `cluster/WriteRoutingDecorator.java`, #486, #487
**Exploration:** quick
**Status:** captured

## D2: Split-brain fallback safety mechanism

**Choice:** CDI event + configurable fail-fast mode. `ProxyFallbackEvent` fires on every proxy failure. Config `casehub.qhorus.relay.proxy-fallback` with values `local` (default, backward-compatible) and `fail` (throw after event fires).
**Alternatives:**
- Reconciliation mechanism — detect and resolve divergent writes via a tracking table. Too complex for S/Med scope; would be its own epic.
- CDI event only — minimal change but forces every consumer wanting CP semantics to build their own exception-throwing observer.
**Rationale:** The CDI event is the floor — every consumer gets notified regardless of mode. The fail-fast config is cheap to add and covers the CP-vs-AP infrastructure choice without requiring consumer-side boilerplate.
**Trade-offs:** No reconciliation — divergent writes in `local` mode are still possible. Consumers must handle `ProxyFallbackEvent` or configure `fail` mode to prevent them.
**Sources:** `cluster/WriteRoutingDecorator.java:52-63`, `cluster/RelayConfig.java`, #475
**Exploration:** quick
**Status:** captured

## D3: Cache module CDI wiring and health test approach

**Choice:** Follow the `ClusterCdiWiringTest` / `ClusterDisabledTest` pattern. Add `@QuarkusTest` CDI wiring test (beans resolve when enabled), disabled test (beans absent when disabled), and health endpoint test. Standalone REST endpoint at `/health/cache` (no SmallRye Health integration).
**Alternatives:**
- SmallRye Health integration (`@Liveness`/`@Readiness`) — standard Quarkus pattern, but changes response format and adds a dependency; behavior change beyond #476 scope
- CDI-free tests only — wouldn't validate actual bean resolution in a Quarkus container
**Rationale:** Consistent with existing cluster module patterns. The cache module already has comprehensive CDI-free unit tests; the gap is container-level wiring verification and health endpoint coverage.
**Trade-offs:** No SmallRye Health aggregation — cache health is only visible via `/health/cache`, not `/q/health`.
**Sources:** `cluster/ClusterCdiWiringTest.java`, `cluster/ClusterDisabledTest.java`, `cache/CacheHealthResource.java`, `cache/CacheProducer.java`, #476
**Exploration:** quick
**Status:** captured
