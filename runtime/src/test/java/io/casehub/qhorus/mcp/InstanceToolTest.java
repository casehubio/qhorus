package io.casehub.qhorus.mcp;

import io.casehub.qhorus.api.channel.ChannelCreateRequest;
import io.casehub.qhorus.api.instance.Instance;
import io.casehub.qhorus.api.instance.InstanceInfo;
import io.casehub.qhorus.api.instance.InstanceManager;
import io.casehub.qhorus.runtime.channel.ChannelService;
import io.casehub.qhorus.runtime.instance.InstanceService;
import io.casehub.qhorus.testing.QhorusTestHelper;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class InstanceToolTest {

    @Inject QhorusTestHelper helper;

    @Inject
    ChannelService channelService;

    @Inject
    InstanceService instanceService;
    @Inject
    InstanceManager instanceManager;


    @Test
    @TestTransaction
    void registerCreatesInstance() {
        Instance result = instanceManager.register("test-agent", "A test agent", List.of("code-review", "java"), false);

        assertEquals("test-agent", result.instanceId());
        assertTrue(instanceManager.listInfo().stream().anyMatch(i -> "test-agent".equals(i.instanceId())),
                "registered instance should appear in list");
    }

    @Test
    @TestTransaction
    void registerUpsertsSameInstanceIdWithoutDuplicate() {
        instanceManager.register("upsert-agent", "First", List.of("python"), false);
        instanceManager.register("upsert-agent", "Updated description", List.of("ml"), false);

        long count = instanceManager.listInfo().stream()
                .filter(i -> "upsert-agent".equals(i.instanceId())).count();
        assertEquals(1, count, "re-registering same instance_id should not create duplicates");
    }

    @Test
    @TestTransaction
    void registerReplacesCapabilityTagsOnUpsert() {
        instanceManager.register("cap-upsert-agent", "Agent", List.of("python"), false);

        instanceManager.register("cap-upsert-agent", "Agent", List.of("ml"), false);

        assertTrue(instanceService.findByCapability("python").isEmpty(),
                "'python' tag should be removed after re-register");
        assertEquals(1, instanceService.findByCapability("ml").size(),
                "'ml' tag should be present after re-register");
    }

    @Test
    @TestTransaction
    void registerStoresClaudonySessionId() {
        instanceService.register("claudony-agent", "Claudony-managed", List.of(), "claudony-session-xyz", false);

        Instance inst = instanceService.findByInstanceId("claudony-agent").orElseThrow();
        assertEquals("claudony-session-xyz", inst.claudonySessionId(),
                "claudonySessionId should be persisted when provided");
    }

    @Test
    @TestTransaction
    void registerWithNoClaudonySessionIdLeavesFieldNull() {
        instanceManager.register("plain-agent", "No claudony", List.of(), false);

        Instance inst = instanceService.findByInstanceId("plain-agent").orElseThrow();
        assertNull(inst.claudonySessionId(),
                "claudonySessionId should be null when not provided");
    }

    @Test
    @TestTransaction
    void listInfoReturnsAllOnline() {
        instanceManager.register("l-agent-1", "Agent 1", List.of("skill-a"), false);
        instanceManager.register("l-agent-2", "Agent 2", List.of("skill-b"), false);

        List<InstanceInfo> all = instanceManager.listInfo();

        assertTrue(all.stream().anyMatch(i -> "l-agent-1".equals(i.instanceId())));
        assertTrue(all.stream().anyMatch(i -> "l-agent-2".equals(i.instanceId())));
    }

    @Test
    @TestTransaction
    void findInfoByCapabilityFilters() {
        instanceManager.register("py-agent", "Python expert", List.of("python"), false);
        instanceManager.register("jv-agent", "Java expert", List.of("java"), false);

        List<InstanceInfo> pythonOnly = instanceManager.findInfoByCapability("python");

        assertEquals(1, pythonOnly.size());
        assertEquals("py-agent", pythonOnly.get(0).instanceId());
    }

    @Test
    @TestTransaction
    void listInfoIncludesCapabilities() {
        instanceManager.register("multi-agent", "Multi-skill", List.of("code-review", "testing"), false);

        List<InstanceInfo> all = instanceManager.listInfo();
        InstanceInfo agent = all.stream()
                .filter(i -> "multi-agent".equals(i.instanceId()))
                .findFirst().orElseThrow();

        assertTrue(agent.capabilities().contains("code-review"));
        assertTrue(agent.capabilities().contains("testing"));
    }

    @Test
    @TestTransaction
    void findInfoByCapabilityWithNoMatchReturnsEmpty() {
        instanceManager.register("solo-agent", "Solo", List.of("python"), false);

        List<InstanceInfo> result = instanceManager.findInfoByCapability("no-such-cap");

        assertTrue(result.isEmpty());
    }
}
