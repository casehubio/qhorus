# Cluster Hardening — E2E Coverage, Split-Brain Safety, Cache Wiring

**Issue:** #488 (epic)
**Branch:** `issue-488-cluster-hardening`
**Date:** 2026-10-08

## Overview

Follow-up from #485, #486, #487 (cluster bug fixes). Three sub-tasks harden the cluster layer: end-to-end validation of cross-node dispatch, safety mechanisms for split-brain proxy failures, and CDI wiring verification for the cache module.

## 1. Cross-node dispatch E2E test

**Scope:** XS, Low

Add a test to `DispatchRoutingE2ETest` that exercises the cross-node proxy path.

### Test design

A new test `cross_node_dispatch_via_proxy` will:
1. Create a channel on node-a (node-a becomes owner via dynamic routing)
2. Send a message from node-b to that channel (triggers `WriteRoutingDecorator` → `WriteProxyClient` → `InternalMeshResource` on node-a)
3. Assert the message is visible when queried from node-a (the owner that received the proxied write)
4. Assert the message is also visible when queried from node-b (shared PostgreSQL)

This validates the #486/#487 fix (`RoutingConsumerMessaging` now routes through `WriteRoutingDecorator`) end-to-end through the container stack.

### Files changed

- `e2e-cluster/src/test/java/.../DispatchRoutingE2ETest.java` — add test method

## 2. Split-brain fallback safety

**Scope:** S, Med

### Problem

`WriteRoutingDecorator.dispatch()` catches proxy failures and silently falls back to local dispatch (WARN log only). This creates unreconciled writes on a non-owner node with no programmatic signal to consumers.

### Design

Two mechanisms, both active on every proxy failure:

#### 2a. ProxyFallbackEvent (CDI event)

A CDI event that fires on every proxy failure, regardless of configuration mode.

```java
package io.casehub.qhorus.cluster;

public record ProxyFallbackEvent(
    UUID channelId,
    String ownerNodeId,
    String localNodeId,
    String sender,
    MessageType messageType,
    String errorMessage
) {}
```

Fired asynchronously via `Event<ProxyFallbackEvent>.fireAsync()` — non-blocking, does not affect the dispatch outcome. Consumers (watchdog bridges, alerting, future reconciliation) observe this event to react.

#### 2b. Configurable fail-fast mode

New config property in `RelayConfig`:

```java
@WithDefault("local")
String proxyFallback();  // "local" | "fail"
```

- `local` (default): current behavior — fire event, fall back to local dispatch. Backward-compatible.
- `fail`: fire event, then throw `ProxyDispatchException extends RuntimeException`. The write does not land anywhere. The caller (MCP tool layer, REST resource) surfaces the error.

`ProxyDispatchException` carries the same fields as the event plus the original exception as cause.

#### 2c. WriteRoutingDecorator changes

The catch block becomes:

```java
} catch (Exception e) {
    LOG.warnf("Proxy to %s failed: %s", owner.nodeId(), e.getMessage());
    var event = new ProxyFallbackEvent(
        dispatch.channelId(), owner.nodeId(), localNodeId,
        dispatch.sender(), dispatch.type(), e.getMessage());
    fallbackEvent.fireAsync(event);
    if ("fail".equals(proxyFallback)) {
        throw new ProxyDispatchException(event, e);
    }
    DispatchResult result = delegate.dispatch(dispatch);
    if (tracker != null) {
        tracker.recordWrite(dispatch.channelId());
    }
    return result;
}
```

The decorator needs three new constructor parameters: `Event<ProxyFallbackEvent> fallbackEvent`, `String proxyFallback`, and `String localNodeId`. Since `WriteRoutingDecorator` is a POJO (created by `RelayProducer.consumerMessaging()`, not a CDI bean), `RelayProducer` injects `Event<ProxyFallbackEvent>` and `RelayConfig` and passes the values through to the constructor. The existing 4-arg and 5-arg constructors remain for backward compatibility (unit tests) — they pass `null` for the event, `"local"` for fallback mode, and `null` for localNodeId (event firing and fail-fast are no-ops when event is null).

#### Files changed

- `cluster/src/main/java/.../ProxyFallbackEvent.java` — new record
- `cluster/src/main/java/.../ProxyDispatchException.java` — new exception
- `cluster/src/main/java/.../WriteRoutingDecorator.java` — add event firing and fail-fast check
- `cluster/src/main/java/.../RelayConfig.java` — add `proxyFallback()` property
- `cluster/src/main/java/.../RelayProducer.java` — wire new constructor args
- `cluster/src/test/java/.../WriteRoutingDecoratorTest.java` — add tests for event firing, fail-fast mode, and local-fallback-still-works

### Test plan

Unit tests (CDI-free, Mockito):
- Proxy failure in `local` mode: fires event, falls back to local dispatch, returns result
- Proxy failure in `fail` mode: fires event, throws `ProxyDispatchException`, no local dispatch
- Successful proxy: no event fired
- Local dispatch (owner is self): no event fired

## 3. Cache module CDI wiring and health

**Scope:** S, Med (tracked as #476)

### Problem

The cache module has comprehensive CDI-free unit tests but no `@QuarkusTest` validating that beans resolve correctly in a real Quarkus container. This gap was exposed by the Jandex index requirement (#484).

### Design

Follow the `ClusterCdiWiringTest` / `ClusterDisabledTest` pattern exactly.

#### 3a. CacheCdiWiringTest

`@QuarkusTest` with `@TestProfile` that sets `casehub.qhorus.cache.enabled=true`. Verifies:
- `CachingMessageStore` is resolvable and is the active `MessageStore` alternative
- `FullSyncService` is resolvable
- `CachePopulationObserver` is resolvable
- `CacheHealthResource` is resolvable

Uses `ClientProxy.unwrap()` for `instanceof` checks on CDI-produced beans.

#### 3b. CacheDisabledTest

`@QuarkusTest` with `@TestProfile` that sets `casehub.qhorus.cache.enabled=false`. Verifies:
- `CachingMessageStore` is not resolvable (Instance.isResolvable() returns false)
- `FullSyncService` is not resolvable

#### 3c. CacheHealthResourceTest

`@QuarkusTest` with cache enabled. Hits `GET /health/cache` and asserts:
- Status 200
- JSON body has `status`, `channelsCached`, `messagesCached` fields
- `status` is `"UP"` when cache is enabled

#### 3d. POM changes

Add test dependencies to `cache/pom.xml`:
- `quarkus-junit5` (test)
- `quarkus-junit5-mockito` (test)
- `casehub-platform` (test — provides `MockCurrentPrincipal`)
- `casehub-qhorus-persistence-memory` (test — InMemory stores)
- H2 database (test)
- REST Assured (test)

#### 3e. Test application.properties

Standard test config: H2 datasource for both default and qhorus named PUs, `drop-and-create` schema generation, random test port.

### Files changed

- `cache/pom.xml` — add test dependencies
- `cache/src/test/resources/application.properties` — test config
- `cache/src/test/java/.../CacheCdiWiringTest.java` — enabled wiring test
- `cache/src/test/java/.../CacheDisabledTest.java` — disabled wiring test
- `cache/src/test/java/.../CacheHealthResourceTest.java` — health endpoint test
- Test profiles as inner classes or standalone (matching cluster module pattern)

## Implementation order

1. Sub-task 1 (E2E test) — independent, no code changes needed
2. Sub-task 2 (split-brain safety) — `WriteRoutingDecorator` changes + new classes
3. Sub-task 3 (cache CDI wiring) — independent module, no cross-dependencies

Sub-tasks 1 and 3 are fully independent. Sub-task 2 is also independent but changes cluster module code, so it should be committed before the E2E test to avoid a rebase.

## References

- `e2e-cluster/src/test/java/.../DispatchRoutingE2ETest.java` — existing E2E tests
- `e2e-cluster/src/test/java/.../ClusterTestHarness.java` — container harness
- `cluster/src/main/java/.../WriteRoutingDecorator.java:52-63` — current fallback code
- `cluster/src/main/java/.../RelayConfig.java` — existing relay config
- `cluster/src/main/java/.../RelayProducer.java` — CDI wiring for cluster beans
- `cluster/src/test/java/.../ClusterCdiWiringTest.java` — pattern for CDI wiring tests
- `cluster/src/test/java/.../ClusterDisabledTest.java` — pattern for disabled tests
- `cache/src/main/java/.../CacheProducer.java` — cache CDI producer
- `cache/src/main/java/.../CacheHealthResource.java` — existing health endpoint
- #475 (distributed mesh epic)
- #476 (cache module tracking issue)
- #484 (E2E audit)
- #485, #486, #487 (cluster bug fixes)
