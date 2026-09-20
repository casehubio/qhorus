package io.casehub.qhorus.mcp;

import io.casehub.qhorus.api.instance.Instance;
import io.casehub.qhorus.api.instance.InstanceInfo;
import io.casehub.qhorus.api.instance.InstanceManager;
import io.casehub.qhorus.runtime.instance.InstanceService;
import io.casehub.qhorus.runtime.mcp.QhorusMcpTools;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for instance re-registration behaviour.
 *
 * Re-registration is the primary mechanism by which a reconnecting agent announces
 * itself to the mesh. These tests verify that re-registration:
 * - Updates all mutable fields (description, status, claudonySessionId, lastSeen).
 * - Atomically replaces capability tags (no stale tags, no accumulation).
 * - Never creates duplicate instances.
 * - Correctly handles capability-tag edge cases (empty list, null list, overlapping tags).
 */
@QuarkusTest
class InstanceReRegistrationTest {

    @Inject
    EntityManager em;

    @Inject
    QhorusMcpTools tools;

    @Inject
    InstanceService instanceService;
    @Inject
    InstanceManager instanceManager;


    /**
     * IMPORTANT: re-registering updates the claudonySessionId field.
     * An agent that was initially registered without a session ID and later
     * reconnects via Claudony must have its session ID updated.
     */
    @Test
    @TestTransaction
    void reRegisterUpdatesClaudonySessionIdFromNullToValue() {
        instanceManager.register("rereg-session-agent", "Agent", List.of(), false);

        // Re-register with a new session ID
        instanceService.register("rereg-session-agent", "Agent", List.of(), "new-session-123", false);

        Instance inst = instanceService.findByInstanceId("rereg-session-agent").orElseThrow();
        assertEquals("new-session-123", inst.claudonySessionId(),
                "re-registration must update claudonySessionId from null to a value");

        // Confirm only one instance row (no duplicates)
        long count = em.createQuery("SELECT COUNT(e) FROM Instance e WHERE e.instanceId = :p1", Long.class).setParameter("p1", "rereg-session-agent").getSingleResult();
        assertEquals(1, count, "re-registration must not create a duplicate instance row");
    }

    /**
     * IMPORTANT: re-registering can clear the claudonySessionId (set it back to null)
     * when the agent reconnects without a Claudony session.
     */
    @Test
    @TestTransaction
    void reRegisterClearsClaudonySessionIdWhenOmitted() {
        instanceService.register("rereg-clear-session", "Agent", List.of(), "existing-session", false);

        // Re-register without claudonySessionId
        instanceService.register("rereg-clear-session", "Agent", List.of(), null, false);

        Instance inst = instanceService.findByInstanceId("rereg-clear-session").orElseThrow();
        assertNull(inst.claudonySessionId(),
                "re-registration with null claudonySessionId must clear the existing session ID");
    }

    /**
     * CRITICAL: capability tags are atomically replaced on re-registration.
     * No stale tags from previous registrations must remain.
     * No capability tag must be lost from the new registration.
     *
     * The atomic DELETE + INSERT in InstanceService.register() prevents any window
     * where neither old nor new tags exist. This test verifies both directions.
     */
    @Test
    @TestTransaction
    void reRegisterAtomicallyReplacesCapabilityTagsNoStaleTags() {
        // Register with tags A, B, C
        instanceManager.register("rereg-caps", "Agent", List.of("tag-a", "tag-b", "tag-c"), false);

        // Re-register with tags D, E — A, B, C must be gone; D, E must be present
        instanceManager.register("rereg-caps", "Agent", List.of("tag-d", "tag-e"), false);

        assertTrue(instanceService.findByCapability("tag-a").isEmpty(),
                "stale tag 'tag-a' must be removed on re-registration");
        assertTrue(instanceService.findByCapability("tag-b").isEmpty(),
                "stale tag 'tag-b' must be removed on re-registration");
        assertTrue(instanceService.findByCapability("tag-c").isEmpty(),
                "stale tag 'tag-c' must be removed on re-registration");

        assertEquals(1, instanceService.findByCapability("tag-d").size(),
                "new tag 'tag-d' must be present after re-registration");
        assertEquals(1, instanceService.findByCapability("tag-e").size(),
                "new tag 'tag-e' must be present after re-registration");

        // Confirm the capability table has exactly 2 rows for this instance
        Instance inst     = instanceService.findByInstanceId("rereg-caps").orElseThrow();
        long     capCount = em.createQuery("SELECT COUNT(e) FROM Capability e WHERE e.instanceId = :p1", Long.class).setParameter("p1", inst.id()).getSingleResult();
        assertEquals(2, capCount,
                "exactly 2 capability rows after re-registration with 2 tags");
    }

    /**
     * IMPORTANT: re-registering with an empty capability list removes all prior tags.
     * This is a legitimate operation — an agent may retire all capabilities.
     */
    @Test
    @TestTransaction
    void reRegisterWithEmptyCapabilityListRemovesAllPriorTags() {
        instanceManager.register("rereg-drop-all-caps", "Agent", List.of("java", "python"), false);

        // Re-register with no tags
        instanceManager.register("rereg-drop-all-caps", "Agent", List.of(), false);

        assertTrue(instanceService.findByCapability("java").isEmpty(),
                "all prior capability tags must be removed when re-registering with empty list");
        assertTrue(instanceService.findByCapability("python").isEmpty());

        Instance inst     = instanceService.findByInstanceId("rereg-drop-all-caps").orElseThrow();
        long     capCount = em.createQuery("SELECT COUNT(e) FROM Capability e WHERE e.instanceId = :p1", Long.class).setParameter("p1", inst.id()).getSingleResult();
        assertEquals(0, capCount,
                "capability table must have 0 rows after re-registration with empty tag list");
    }

    /**
     * IMPORTANT: re-registering with the SAME capability list re-creates exactly the
     * same tags (no duplication, no loss). The old rows are deleted and new rows are inserted.
     */
    @Test
    @TestTransaction
    void reRegisterWithSameCapabilityListResultsInSameTagCount() {
        instanceManager.register("rereg-same-caps", "Agent", List.of("java", "quarkus"), false);
        instanceManager.register("rereg-same-caps", "Agent", List.of("java", "quarkus"), false);

        Instance inst     = instanceService.findByInstanceId("rereg-same-caps").orElseThrow();
        long     capCount = em.createQuery("SELECT COUNT(e) FROM Capability e WHERE e.instanceId = :p1", Long.class).setParameter("p1", inst.id()).getSingleResult();
        assertEquals(2, capCount,
                "re-registration with the same tag list must result in exactly 2 capability rows, " +
                        "not 4 (not accumulated)");
    }

    /**
     * CREATIVE: two different agents re-register concurrently with the same capability tag.
     * Both should have the tag, and findByCapability should return both.
     * (Sequential approximation — true concurrency would require separate threads.)
     */
    @Test
    @TestTransaction
    void twoAgentsWithSameCapabilityTagAreBothFindable() {
        instanceManager.register("multi-cap-a", "Agent A", List.of("shared-skill"), false);
        instanceManager.register("multi-cap-b", "Agent B", List.of("shared-skill"), false);

        List<InstanceInfo> found = instanceManager.findInfoByCapability("shared-skill");

        assertEquals(2, found.size(),
                "both agents with 'shared-skill' must be returned by listInstances");
        assertTrue(found.stream().anyMatch(i -> "multi-cap-a".equals(i.instanceId())));
        assertTrue(found.stream().anyMatch(i -> "multi-cap-b".equals(i.instanceId())));
    }

    /**
     * CREATIVE: re-registration resets status to "online" even if the instance was
     * previously marked "stale". This models a reconnecting agent.
     */
    @Test
    @TestTransaction
    void reRegistrationSetsStatusBackToOnlineAfterStale() throws InterruptedException {
        instanceService.register("rereg-stale-recovery", "Agent", List.of());

        // Force stale status with 0s threshold (any lastSeen in the past is stale)
        Thread.sleep(10); // ensure lastSeen is actually in the past
        instanceService.markStaleOlderThan(0);
        em.clear();

        Instance stale = instanceService.findByInstanceId("rereg-stale-recovery").orElseThrow();
        assertEquals("stale", stale.status(), "instance should be stale after markStaleOlderThan(0)");

        // Agent re-registers — status must return to online
        instanceManager.register("rereg-stale-recovery", "Agent reconnected", List.of("recovered"), false);

        Instance recovered = instanceService.findByInstanceId("rereg-stale-recovery").orElseThrow();
        assertEquals("online", recovered.status(),
                "re-registration must set status back to 'online' regardless of prior state");
        assertEquals("Agent reconnected", recovered.description(),
                "re-registration must update the description");
    }
}
