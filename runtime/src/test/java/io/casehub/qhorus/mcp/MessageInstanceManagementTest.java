package io.casehub.qhorus.mcp;

import io.casehub.qhorus.api.instance.InstanceInfo;
import io.casehub.qhorus.api.instance.InstanceManager;
import io.casehub.qhorus.api.message.DispatchResult;
import io.casehub.qhorus.testing.QhorusTestHelper;
import io.casehub.qhorus.testing.QhorusTestHelper.ClearChannelResult;
import io.casehub.qhorus.testing.QhorusTestHelper.DeleteMessageResult;
import io.casehub.qhorus.api.message.Message;

import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Issue #42 — var and instance management: delete_message, clear_channel, deregister_instance.
 *
 * <p>
 * Three surgical control tools for incident response:
 * <ul>
 * <li>{@code delete_message(message_id)} — removes a single message; event posted</li>
 * <li>{@code clear_channel(channel_name)} — removes all non-event messages; returns count</li>
 * <li>{@code deregister_instance(instance_id)} — force-removes agent and capabilities</li>
 * </ul>
 *
 * <p>
 * Refs #42, Epic #36.
 */
@QuarkusTest
class MessageInstanceManagementTest {

    @Inject QhorusTestHelper helper;
    @Inject
    InstanceManager instanceManager;


    // =========================================================================
    // delete_message
    // =========================================================================

    @Test
    @TestTransaction
    void deleteMessageRemovesItFromChannel() {
        helper.createChannel("mim-del-1", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        DispatchResult msg = helper.sendMessage("mim-del-1", "alice", "status", "bad message", null, null, null, null, null, null, null, null, null);

        var result = helper.deleteMessage(msg.messageId());

        assertTrue(result.deleted());
        assertEquals(msg.messageId(), result.messageId());

        // var no longer appears in check_messages
        var check = helper.checkMessages("mim-del-1", 0L, 10, null, null, null);
        assertTrue(check.isEmpty(), "deleted message should not appear in check_messages");
    }

    @Test
    @TestTransaction
    void deleteMessageReturnsMetadata() {
        helper.createChannel("mim-del-2", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        DispatchResult msg = helper.sendMessage("mim-del-2", "alice", "status", "the content", null, null, null, null, null, null, null, null, null);

        var result = helper.deleteMessage(msg.messageId());

        assertTrue(result.deleted());
        assertEquals("alice", result.sender());
        assertEquals("STATUS", result.messageType());
        assertNotNull(result.contentPreview());
    }

    @Test
    @TestTransaction
    void deleteMessageUnknownIdReturnsFalse() {
        var result = helper.deleteMessage(Long.MAX_VALUE);

        assertFalse(result.deleted(), "deleting unknown message id should return deleted=false");
        assertNotNull(result.message());
    }

    @Test
    @TestTransaction
    void deleteMessageDoesNotCascadeToReplies() {
        helper.createChannel("mim-del-3", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        DispatchResult parent = helper.sendMessage("mim-del-3", "alice", "query", "question", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("mim-del-3", "bob", "response", "answer", null, parent.correlationId(), parent.messageId(), null, null, null, null, null, null);

        // Delete the parent — replies should still exist
        helper.deleteMessage(parent.messageId());

        var check = helper.checkMessages("mim-del-3", 0L, 10, null, null, null);
        assertEquals(1, check.size(), "reply should survive parent deletion");
        assertEquals("answer", check.get(0).content());
    }

    // =========================================================================
    // clear_channel
    // =========================================================================

    @Test
    @TestTransaction
    void clearChannelDeletesAllNonEventMessages() {
        helper.createChannel("mim-clear-1", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        var cmd = helper.sendMessage("mim-clear-1", "alice", "command", "msg1", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("mim-clear-1", "bob", "response", "msg2", null, cmd.correlationId(), cmd.messageId(), null, null, null, null, null, null);
        helper.sendMessage("mim-clear-1", "carol", "status", "msg3", null, null, null, null, null, null, null, null, null);

        ClearChannelResult result = helper.clearChannel("mim-clear-1", null);

        assertEquals(3, result.messagesDeleted(), "should report 3 messages deleted");
        assertTrue(result.cleared());
    }

    @Test
    @TestTransaction
    void clearChannelMakesCheckMessagesReturnEmpty() {
        helper.createChannel("mim-clear-2", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("mim-clear-2", "alice", "status", "a", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("mim-clear-2", "bob", "status", "b", null, null, null, null, null, null, null, null, null);

        helper.clearChannel("mim-clear-2", null);

        var check = helper.checkMessages("mim-clear-2", 0L, 10, null, null, null);
        assertTrue(check.isEmpty(), "channel should be empty after clear");
    }

    @Test
    @TestTransaction
    void clearEmptyChannelReturnsZeroCount() {
        helper.createChannel("mim-clear-3", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        ClearChannelResult result = helper.clearChannel("mim-clear-3", null);

        assertEquals(0, result.messagesDeleted());
        assertTrue(result.cleared());
    }

    @Test
    @TestTransaction
    void clearChannelUnknownChannelThrows() {
        assertThrows(IllegalArgumentException.class, () -> helper.clearChannel("no-such-channel", null));
    }

    @Test
    @TestTransaction
    void clearChannelPreservesChannelStructure() {
        helper.createChannel("mim-clear-4", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("mim-clear-4", "alice", "status", "msg", null, null, null, null, null, null, null, null, null);

        helper.clearChannel("mim-clear-4", null);

        // Channel itself still exists — can still send messages
        assertDoesNotThrow(() -> helper.sendMessage("mim-clear-4", "alice", "status", "new msg", null, null, null, null, null, null, null, null, null));
    }

    // =========================================================================
    // deregister_instance
    // =========================================================================

    @Test
    @TestTransaction
    void deregisterInstanceRemovesFromRegistry() {
        instanceManager.register("mim-agent-1", "Test agent", List.of(), false);

        instanceManager.deregister("mim-agent-1");

        // No longer in list
        boolean stillPresent = instanceManager.listInfo().stream()
                .anyMatch(i -> "mim-agent-1".equals(i.instanceId()));
        assertFalse(stillPresent, "deregistered instance should not appear in list");
    }

    @Test
    @TestTransaction
    void deregisterInstanceRemovesCapabilityTags() {
        instanceManager.register("mim-agent-2", "Agent with caps", List.of("capability:code-review", "role:reviewer"), false);

        instanceManager.deregister("mim-agent-2");

        // Capabilities should be cleaned up
        List<InstanceInfo> remaining = instanceManager.findInfoByCapability("capability:code-review");
        assertFalse(remaining.stream().anyMatch(i -> "mim-agent-2".equals(i.instanceId())),
                "deregistered instance capabilities should be cleaned up");
    }

    @Test
    @TestTransaction
    void deregisterUnknownInstanceIsNoOp() {
        // Deregistering a non-existent instance is a silent no-op
        assertDoesNotThrow(() -> instanceManager.deregister("no-such-agent"));
    }

    // =========================================================================
    // Integration
    // =========================================================================

    @Test
    @TestTransaction
    void integrationDeleteBadMessageFromChannel() {
        helper.createChannel("mim-int-1", "Work", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("mim-int-1", "alice", "command", "good message", null, null, null, null, null, null, null, null, null);
        DispatchResult bad = helper.sendMessage("mim-int-1", "alice", "status", "PII: name=John", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("mim-int-1", "alice", "status", "another good one", null, null, null, null, null, null, null, null, null);

        helper.deleteMessage(bad.messageId());

        var check = helper.checkMessages("mim-int-1", 0L, 10, null, null, null);
        assertEquals(2, check.size(), "only the bad message should be removed");
        assertTrue(check.stream().noneMatch(m -> m.content().contains("PII")));
    }

    // =========================================================================
    // E2E
    // =========================================================================

    @Test
    @TestTransaction
    void e2eHumanDeletesPIIThenClearsChannel() {
        helper.createChannel("mim-e2e-1", "Sensitive Work", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        // Agents post work
        DispatchResult piiMsg = helper.sendMessage("mim-e2e-1", "agent-1", "status", "User SSN: 123-45-6789", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("mim-e2e-1", "agent-2", "status", "Legitimate work output", null, null, null, null, null, null, null, null, null);

        // Human deletes specific PII message
        helper.deleteMessage(piiMsg.messageId());

        // Only legitimate message remains
        var afterDelete = helper.checkMessages("mim-e2e-1", 0L, 10, null, null, null);
        assertEquals(1, afterDelete.size());

        // Human decides to clear the whole channel
        helper.clearChannel("mim-e2e-1", null);
        assertTrue(helper.checkMessages("mim-e2e-1", 0L, 10, null, null, null).isEmpty());
    }

    @Test
    @TestTransaction
    void e2eHumanDeregistersRogueAgent() {
        instanceManager.register("rogue-agent", "Misbehaving agent", List.of("capability:code-review"), false);
        helper.createChannel("mim-e2e-2", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("mim-e2e-2", "rogue-agent", "status", "rogue message", null, null, null, null, null, null, null, null, null);

        // Human deregisters the rogue agent
        instanceManager.deregister("rogue-agent");

        // Agent gone from registry
        assertFalse(instanceManager.listInfo().stream()
                .anyMatch(i -> "rogue-agent".equals(i.instanceId())));

        // Its past messages still exist (deregister doesn't delete messages)
        var check = helper.checkMessages("mim-e2e-2", 0L, 10, null, null, null);
        assertEquals(1, check.size(), "past messages survive deregistration");
    }
}
