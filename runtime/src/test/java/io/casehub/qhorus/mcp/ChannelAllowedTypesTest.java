package io.casehub.qhorus.mcp;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehub.qhorus.testing.QhorusTestHelper;
import io.casehub.qhorus.api.channel.ChannelDetail;
import io.casehub.qhorus.api.message.DispatchResult;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class ChannelAllowedTypesTest {

    @Inject QhorusTestHelper helper;

    @Test
    @TestTransaction
    void createChannel_withAllowedTypes_roundtripsInDetail() {
        ChannelDetail detail = helper.createChannel(
                "oversight-" + System.nanoTime(), "Human governance", "APPEND",
                null, null, null, null, null, "QUERY,COMMAND", null, null, null, null, null, null, null, null, null, null);
        // Canonical sorted form: COMMAND < QUERY alphabetically
        assertEquals("COMMAND,QUERY", detail.allowedTypes());
    }

    @Test
    @TestTransaction
    void createChannel_nullAllowedTypes_detailShowsNull() {
        ChannelDetail detail = helper.createChannel("open-" + System.nanoTime(), "Open channel", "APPEND", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        assertNull(detail.allowedTypes());
    }

    @Test
    @TestTransaction
    void createChannel_existingFourParamOverload_detailShowsNull() {
        ChannelDetail detail = helper.createChannel("legacy-" + System.nanoTime(), "Legacy call", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        assertNull(detail.allowedTypes());
    }

    @Test
    @TestTransaction
    void sendMessage_rejectsDisallowedType_serverSide() {
        String name = "observe-enforce-" + System.nanoTime();
        helper.createChannel(name, "Telemetry only", "APPEND", null, null, null, null, null, "EVENT", null, null, null, null, null, null, null, null, null, null);
        assertThrows(Exception.class, () -> helper.sendMessage(name, "agent-1", "QUERY", "hello?", null, null, null, null, null, null, null, null, null));
    }

    @Test
    @TestTransaction
    void sendMessage_permitsAllowedType_clientSide() {
        String name = "observe-ok-" + System.nanoTime();
        helper.createChannel(name, "Telemetry only", "APPEND", null, null, null, null, null, "EVENT", null, null, null, null, null, null, null, null, null, null);
        assertDoesNotThrow(() -> helper.sendMessage(name, "agent-1", "EVENT", null, null, null, null, null, null, null, null, null, null));
    }

    @Test
    @TestTransaction
    void sendMessage_openChannel_permitsAllTypes() {
        String name = "open-all-" + System.nanoTime();
        helper.createChannel(name, "Open", "APPEND", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        assertDoesNotThrow(() -> helper.sendMessage(name, "agent-1", "COMMAND", "do something", null, null, null, null, null, null, null, null, null));
    }

    @Test
    @TestTransaction
    void advisory_mentionsChannelAndType() {
        String name = "oversight-block-" + System.nanoTime();
        helper.createChannel(name, "Governance", "APPEND",
                            null, null, null, null, null, "QUERY,COMMAND", null, null, null, null, null, null, null, null, null, null);
        // EVENT is not obligation-creating — dispatch succeeds with advisory (content must be null for EVENT)
        DispatchResult result = helper.sendMessage(name, "agent-1", "EVENT", null, null, null, null, null, null, null, null, null, null);
        assertFalse(result.advisories().isEmpty(), "Expected advisory for EVENT on constrained channel");
        assertTrue(result.advisories().get(0).contains("EVENT"), "Advisory should name the type");
        assertTrue(result.advisories().get(0).contains("Message dispatched."), "Advisory should confirm dispatch");
    }
}
