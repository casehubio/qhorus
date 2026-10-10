# Stage 2: Registry Migration — Replace Fragmented Registries

> **For agentic workers:** REQUIRED SUB-SKILL: Use
> subagent-driven-development (recommended) or executing-plans to
> implement this plan task-by-task. Each task follows TDD
> (test-driven-development) and uses ide-tooling for structural
> editing. Steps use checkbox (`- [ ]`) syntax for tracking.

**Focal issue:** #267 — modular fleet architecture
**Issue group:** #267 (epic)
**Depends on:** Stage 1 (registry-core) must be complete and installed to local Maven repo

**Goal:** Replace four existing per-component registries with adapters backed by the unified `RegistryService` SPI from Stage 1. Strangler fig pattern — each migration is independent, old APIs preserved.

**Architecture:** Each existing registry gets a thin adapter that delegates to `RegistryService` using a specific `type` string. The adapter implements the same interface as the original, so consumers don't change. Old implementations are removed once the adapter is proven. Each migration is a standalone PR.

**Tech Stack:** Java 21, Quarkus 3.32.2, CDI

**Repos:** `casehub-platform` (for install), `casehub-qhorus`, `casehub-claudony`

## Global Constraints

- Existing consumer APIs must not change — adapters implement the same interfaces
- Each migration is independently testable and deployable
- Stage 1's `casehub-platform-registry-memory` must be installed to `~/.m2` before starting
- Run `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn install -DskipTests -pl platform-api,registry-memory -f ~/claude/casehub/platform/pom.xml` first

---

## Batch 1: Qhorus InstanceManager Migration

### Task 1: RegistryBackedInstanceManager — wrap RegistryService as InstanceManager

**Repo:** `casehub-qhorus`

**Files:**
- Create: `runtime/src/main/java/io/casehub/qhorus/runtime/instance/RegistryBackedInstanceManager.java`
- Modify: `api/pom.xml` — add `casehub-platform-api` dependency (for RegistryService SPI)
- Modify: `runtime/pom.xml` — add `casehub-platform-registry-memory` test dependency
- Test: `runtime/src/test/java/io/casehub/qhorus/runtime/instance/RegistryBackedInstanceManagerTest.java`

**Interfaces:**
- Consumes: `RegistryService` (Stage 1), `InstanceManager` (existing Qhorus SPI)
- Produces: `RegistryBackedInstanceManager` — drop-in replacement for JPA-based instance store

- [ ] **Step 1: Write tests for RegistryBackedInstanceManager**

```java
package io.casehub.qhorus.runtime.instance;

import io.casehub.platform.api.registry.*;
import io.casehub.platform.registry.memory.InMemoryRegistryService;
import io.casehub.qhorus.api.instance.InstanceInfo;
import io.casehub.qhorus.api.instance.InstanceManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class RegistryBackedInstanceManagerTest {

    private InMemoryRegistryService registry;
    private InstanceManager manager;

    @BeforeEach
    void setUp() {
        registry = new InMemoryRegistryService(e -> {});
        manager = new RegistryBackedInstanceManager(registry, "tenant-1");
    }

    @Test
    void registerCreatesRegistryEntry() {
        var instance = manager.register("agent-1", "Test agent",
            List.of("code-review"), false);
        assertThat(instance).isNotNull();
        assertThat(registry.resolve("agent-1")).isPresent();
        assertThat(registry.resolve("agent-1").get().type())
            .isEqualTo("agent-instance");
    }

    @Test
    void listInfoReturnsRegisteredInstances() {
        manager.register("agent-1", "Agent 1", List.of("review"), false);
        manager.register("agent-2", "Agent 2", List.of("code"), false);
        var infos = manager.listInfo();
        assertThat(infos).hasSize(2);
    }

    @Test
    void findInfoByCapability() {
        manager.register("agent-1", "Agent 1", List.of("review", "code"), false);
        manager.register("agent-2", "Agent 2", List.of("code"), false);
        var results = manager.findInfoByCapability("review");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).instanceId()).isEqualTo("agent-1");
    }

    @Test
    void findInfoById() {
        manager.register("agent-1", "Agent 1", List.of("review"), false);
        var info = manager.findInfo("agent-1");
        assertThat(info).isNotNull();
        assertThat(info.instanceId()).isEqualTo("agent-1");
        assertThat(info.description()).isEqualTo("Agent 1");
    }

    @Test
    void findInfoUnknownReturnsNull() {
        assertThat(manager.findInfo("nope")).isNull();
    }

    @Test
    void deregisterRemovesFromRegistry() {
        manager.register("agent-1", "Agent 1", List.of("review"), false);
        manager.deregister("agent-1");
        assertThat(registry.resolve("agent-1")).isEmpty();
        assertThat(manager.listInfo()).isEmpty();
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl runtime -Dtest=RegistryBackedInstanceManagerTest -f ~/claude/casehub/qhorus/pom.xml`
Expected: FAIL — class not found

- [ ] **Step 3: Implement RegistryBackedInstanceManager**

```java
package io.casehub.qhorus.runtime.instance;

import io.casehub.platform.api.registry.*;
import io.casehub.qhorus.api.instance.*;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public class RegistryBackedInstanceManager implements InstanceManager {

    private static final String TYPE = "agent-instance";
    private static final Duration DEFAULT_TTL = Duration.ofMinutes(5);

    private final RegistryService registry;
    private final String tenancyId;

    public RegistryBackedInstanceManager(RegistryService registry, String tenancyId) {
        this.registry = registry;
        this.tenancyId = tenancyId;
    }

    @Override
    public Instance register(String instanceId, String description,
                             List<String> capabilities, boolean readOnly) {
        var metadata = Map.of(
            "description", description,
            "capabilities", String.join(",", capabilities),
            "readOnly", String.valueOf(readOnly)
        );
        var entry = new RegistryEntry(
            instanceId, TYPE, "default", tenancyId,
            metadata, Instant.now(), Instant.now(), DEFAULT_TTL,
            HealthStatus.HEALTHY
        );
        registry.register(entry);
        return new Instance(instanceId, description, capabilities, readOnly);
    }

    @Override
    public List<InstanceInfo> listInfo() {
        return registry.discover(new RegistryQuery(tenancyId, TYPE, null))
            .stream().map(this::toInfo).toList();
    }

    @Override
    public List<InstanceInfo> findInfoByCapability(String capability) {
        return listInfo().stream()
            .filter(i -> i.capabilities().contains(capability))
            .toList();
    }

    @Override
    public InstanceInfo findInfo(String instanceId) {
        return registry.resolve(instanceId)
            .filter(e -> e.type().equals(TYPE))
            .map(this::toInfo)
            .orElse(null);
    }

    @Override
    public void deregister(String instanceId) {
        registry.deregister(instanceId);
    }

    private InstanceInfo toInfo(RegistryEntry entry) {
        var caps = entry.metadata().getOrDefault("capabilities", "");
        var capList = caps.isEmpty() ? List.<String>of() :
            List.of(caps.split(","));
        return new InstanceInfo(
            entry.id(),
            entry.metadata().getOrDefault("description", ""),
            capList,
            Boolean.parseBoolean(entry.metadata().getOrDefault("readOnly", "false"))
        );
    }
}
```

Note: The exact `Instance` and `InstanceInfo` constructors/fields should be verified against the actual Qhorus API classes at implementation time. The adapter pattern remains the same — the field mapping may need adjustment.

- [ ] **Step 4: Run tests to verify they pass**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl runtime -Dtest=RegistryBackedInstanceManagerTest -f ~/claude/casehub/qhorus/pom.xml`
Expected: PASS (6 tests)

- [ ] **Step 5: Commit**

```bash
git -C ~/claude/casehub/qhorus add api/pom.xml runtime/pom.xml runtime/src/
git -C ~/claude/casehub/qhorus commit -m "feat(instance): add RegistryBackedInstanceManager — unified registry adapter Refs casehubio/claudony#267"
```

---

## Batch 2: Claudony PeerRegistry Migration

### Task 2: RegistryBackedPeerRegistry — wrap RegistryService as PeerRegistry

**Repo:** `casehub-claudony`

**Files:**
- Create: `app/src/main/java/io/casehub/claudony/server/fleet/RegistryBackedPeerRegistry.java`
- Modify: `app/pom.xml` — add `casehub-platform-registry-memory` dependency
- Test: `app/src/test/java/io/casehub/claudony/server/fleet/RegistryBackedPeerRegistryTest.java`

**Interfaces:**
- Consumes: `RegistryService` (Stage 1), existing `PeerRegistry` API
- Produces: Drop-in replacement for `PeerRegistry` with registry-backed storage

**Implementation note:** `PeerRegistry` is a concrete class with `ConcurrentHashMap` + file persistence, not an interface. The migration path is:
1. Extract an interface from `PeerRegistry`'s public API
2. Implement the interface in `RegistryBackedPeerRegistry`
3. Wire via CDI (`@Alternative @Priority`)

- [ ] **Step 1: Read current PeerRegistry API to understand the surface**

Read: `app/src/main/java/io/casehub/claudony/server/fleet/PeerRegistry.java`
Identify all public methods that need to be preserved in the extracted interface.

- [ ] **Step 2: Extract PeerRegistry interface**

Use `ide_refactor_rename` or manual extraction. Create interface with the public methods from `PeerRegistry`. Rename the current concrete class to `FilePeerRegistry` and have it implement the new interface.

- [ ] **Step 3: Write tests for RegistryBackedPeerRegistry**

Tests should cover: add peer, remove peer, find by URL, list all, health status mapping, circuit breaker state mapping (if applicable). Use `InMemoryRegistryService` as the backing registry in tests.

- [ ] **Step 4: Implement RegistryBackedPeerRegistry**

Map peer concepts to registry entries:
- `type = "node"`
- `metadata` carries peer URL, name, terminal mode
- Heartbeat maps to peer health check cycle
- Circuit breaker state maps to `HealthStatus` (CLOSED→HEALTHY, OPEN→DOWN, HALF_OPEN→DEGRADED)

- [ ] **Step 5: Wire as CDI alternative**

```java
@Alternative @Priority(100) @ApplicationScoped
```

Only activated when the `RegistryService` is not the NoOp default. Use `@IfBuildProperty` or runtime check.

- [ ] **Step 6: Run existing PeerRegistryTest to ensure backward compatibility**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl app -Dtest=PeerRegistryTest -f ~/claude/casehub/claudony/pom.xml`
Expected: PASS (existing tests unaffected)

- [ ] **Step 7: Commit**

```bash
git -C ~/claude/casehub/claudony add app/src/ app/pom.xml
git -C ~/claude/casehub/claudony commit -m "feat(fleet): add RegistryBackedPeerRegistry — unified registry adapter Refs #267"
```

---

## Batch 3: Pool Definition Registry and PoolMeshRegistrar Migration

### Task 3: RegistryBackedPoolDefinitionRegistry

**Repo:** `casehub-claudony`

**Files:**
- Create: `casehub/src/main/java/io/casehub/claudony/casehub/fleet/RegistryBackedPoolDefinitionRegistry.java`
- Test: `casehub/src/test/java/io/casehub/claudony/casehub/fleet/RegistryBackedPoolDefinitionRegistryTest.java`

**Interfaces:**
- Consumes: `RegistryService` (Stage 1), existing `AgentPoolDefinitionRegistry` API
- Produces: Drop-in replacement for `AgentPoolDefinitionRegistry`

**Implementation note:** Similar pattern to Task 2. `AgentPoolDefinitionRegistry` is an `@ApplicationScoped` bean with `ConcurrentHashMap` storage. Migration path:
1. Extract interface from public API
2. Implement with `RegistryService` backing (`type = "pool"`)
3. Wire as `@Alternative @Priority`

- [ ] **Step 1: Read current AgentPoolDefinitionRegistry API**

Read: `casehub/src/main/java/io/casehub/claudony/casehub/fleet/AgentPoolDefinitionRegistry.java`

- [ ] **Step 2: Extract interface, implement adapter, write tests**

Map pool definitions to registry entries:
- `type = "pool"`
- `namespace = pool.namespace()` (or "default")
- `metadata` carries agentId, minActive, maxActive, scaling config, eviction policy

- [ ] **Step 3: Run existing tests**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl casehub -Dtest=AgentPoolDefinitionRegistryTest,AgentPoolDefinitionRegistryUpdateTest -f ~/claude/casehub/claudony/pom.xml`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git -C ~/claude/casehub/claudony add casehub/src/
git -C ~/claude/casehub/claudony commit -m "feat(fleet): add RegistryBackedPoolDefinitionRegistry — unified registry adapter Refs #267"
```

### Task 4: Replace PoolMeshRegistrar bridge with registry relationships

**Repo:** `casehub-claudony`

**Files:**
- Modify: `app/src/main/java/io/casehub/claudony/server/fleet/PoolMeshRegistrar.java`
- Test: Modify existing `PoolMeshIntegrationTest`

**Interfaces:**
- Consumes: `RegistryService` (Stage 1)
- Produces: Simplified bridge that creates `Relationship` links instead of directly calling `InstanceService`

**Implementation note:** Currently `PoolMeshRegistrar` implements `SessionLifecycleListener` and calls `instanceService.register()` / `instanceService.markOffline()` / `instanceService.deregister()`. With the unified registry, pool sessions and agent instances are both `RegistryEntry` instances. The bridge becomes:
- `onAcquired` → `registry.link(new Relationship(poolId, sessionId, "contains"))`
- `onSuspended` → update session entry health to `DEGRADED`
- `onDestroyed` → `registry.unlink(poolId, sessionId)`

The Qhorus instance registration (Task 1) handles the agent-instance side; this task handles the pool→session relationship side.

- [ ] **Step 1: Modify PoolMeshRegistrar to use RegistryService**

Inject `RegistryService` alongside existing `InstanceService`. Add relationship management. Keep existing `InstanceService` calls for backward compatibility during transition.

- [ ] **Step 2: Update tests**

Verify that `Relationship` links are created/removed on lifecycle events.

- [ ] **Step 3: Run PoolMeshIntegrationTest**

Run: `JAVA_HOME=$(/usr/libexec/java_home -v 26) mvn test -pl app -Dtest=PoolMeshIntegrationTest -f ~/claude/casehub/claudony/pom.xml`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git -C ~/claude/casehub/claudony add app/src/
git -C ~/claude/casehub/claudony commit -m "feat(fleet): PoolMeshRegistrar uses registry relationships alongside InstanceService Refs #267"
```

---

## References

- `2026-10-09-modular-fleet-architecture-design.md` — design spec
- `2026-10-09-stage1-registry-core.md` — Stage 1 plan (prerequisite)
- `qhorus/api/.../instance/InstanceManager.java` — Qhorus instance SPI
- `claudony/app/.../fleet/PeerRegistry.java` — Claudony peer registry
- `claudony/casehub/.../fleet/AgentPoolDefinitionRegistry.java` — pool definition registry
- `claudony/app/.../fleet/PoolMeshRegistrar.java` — pool→mesh lifecycle bridge
- casehubio/claudony#267 — epic issue
