package io.casehub.qhorus.mcp;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehub.qhorus.runtime.instance.InstanceService;
import io.casehub.qhorus.testing.QhorusTestHelper;
import io.casehub.qhorus.api.instance.InstanceInfo;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class GetInstanceToolTest {

    @Inject QhorusTestHelper helper;
    @Inject
    InstanceService instanceService;

    @Test
    void getInstance_knownId_returnsCorrectInfo() {
        String instanceId = "inst-get-" + System.nanoTime();
        QuarkusTransaction.requiringNew().run(() -> instanceService.register(instanceId, "Test agent", List.of("search", "analysis"), null));

        InstanceInfo info = QuarkusTransaction.requiringNew().call(() -> helper.getInstance(instanceId));

        assertEquals(instanceId, info.instanceId());
        assertEquals("Test agent", info.description());
        assertTrue(info.capabilities().contains("search"));
        assertTrue(info.capabilities().contains("analysis"));

        // Clean up — deregister so the instance does not pollute listInstances() counts in other tests
        QuarkusTransaction.requiringNew().run(() -> helper.deregisterInstance(instanceId));
    }

    @Test
    void getInstance_unknownId_throwsWithNotFoundMessage() {
        Exception ex = assertThrows(Exception.class,
                () -> QuarkusTransaction.requiringNew().run(() -> helper.getInstance("no-such-instance-" + System.nanoTime())));
        assertTrue(ex.getMessage().toLowerCase().contains("not found"),
                "Error should say 'not found': " + ex.getMessage());
    }
}
