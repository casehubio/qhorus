package io.casehub.qhorus.mcp;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehub.qhorus.testing.QhorusTestHelper;
import io.casehub.qhorus.testing.QhorusTestHelper.CheckResult;
import io.casehub.qhorus.api.message.DispatchResult;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class LastWriteSemanticTest {

    @Inject QhorusTestHelper helper;

    @Test
    @TestTransaction
    void lastWriteFirstMessageSucceeds() {
        helper.createChannel("lw-1", "LAST_WRITE channel", "LAST_WRITE", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        DispatchResult result = helper.sendMessage("lw-1", "alice", "status", "v1", null, null, null, null, null, null, null, null, null);

        assertNotNull(result.messageId());
    }

    @Test
    @TestTransaction
    void lastWriteSameSenderOverwritesInPlace() {
        helper.createChannel("lw-2", "LAST_WRITE channel", "LAST_WRITE", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        DispatchResult first = helper.sendMessage("lw-2", "alice", "status", "v1", null, null, null, null, null, null, null, null, null);

        DispatchResult second = helper.sendMessage("lw-2", "alice", "status", "v2", null, null, null, null, null, null, null, null, null);

        // Overwrite in place — same message ID
        assertEquals(first.messageId(), second.messageId(),
                "LAST_WRITE same-sender write should update the existing message, not insert a new one");

        // Channel has exactly one message with updated content
        CheckResult messages = helper.checkMessages("lw-2", 0L, 10, null, null, null);
        assertEquals(1, messages.size());
        assertEquals("v2", messages.get(0).content());
    }

    @Test
    @TestTransaction
    void lastWriteChannelHasExactlyOneMessageAfterMultipleWrites() {
        helper.createChannel("lw-3", "LAST_WRITE channel", "LAST_WRITE", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("lw-3", "alice", "status", "v1", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("lw-3", "alice", "status", "v2", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("lw-3", "alice", "status", "v3", null, null, null, null, null, null, null, null, null);

        CheckResult messages = helper.checkMessages("lw-3", 0L, 10, null, null, null);

        assertEquals(1, messages.size());
        assertEquals("v3", messages.get(0).content());
    }

    @Test
    @TestTransaction
    void lastWriteDifferentSenderIsRejected() {
        helper.createChannel("lw-4", "LAST_WRITE channel", "LAST_WRITE", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("lw-4", "alice", "status", "alice owns this", null, null, null, null, null, null, null, null, null);

        assertThrows(IllegalStateException.class, () -> helper.sendMessage("lw-4", "bob", "status", "bob tries", null, null, null, null, null, null, null, null, null), "LAST_WRITE channel should reject a second sender");
    }

    @Test
    @TestTransaction
    void lastWriteRejectionMessageIdentifiesCurrentWriter() {
        helper.createChannel("lw-5", "LAST_WRITE channel", "LAST_WRITE", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("lw-5", "alice", "status", "alice owns this", null, null, null, null, null, null, null, null, null);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> helper.sendMessage("lw-5", "bob", "status", "bob tries", null, null, null, null, null, null, null, null, null));

        assertTrue(ex.getMessage().contains("alice"),
                "rejection message should identify the current writer");
    }

    @Test
    @TestTransaction
    void lastWriteOverwriteUpdatesMessageType() {
        helper.createChannel("lw-6", "LAST_WRITE channel", "LAST_WRITE", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("lw-6", "alice", "status", "initial state", null, null, null, null, null, null, null, null, null);

        // Overwrite with a different type — should be reflected in the stored message
        DispatchResult overwrite = helper.sendMessage("lw-6", "alice", "command", "updated", null, null, null, null, null, null, null, null, null);

        assertEquals(io.casehub.qhorus.api.message.MessageType.COMMAND, overwrite.type(),
                "LAST_WRITE overwrite should replace messageType, not retain the original");
        CheckResult messages = helper.checkMessages("lw-6", 0L, 10, null, null, null);
        assertEquals(1, messages.size());
        assertEquals("COMMAND", messages.get(0).messageType());
    }

    @Test
    @TestTransaction
    void lastWriteOverwriteUpdatesCorrelationId() {
        helper.createChannel("lw-7", "LAST_WRITE channel", "LAST_WRITE", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("lw-7", "alice", "status", "v1", null, "corr-original", null, null, null, null, null, null, null);

        DispatchResult overwrite = helper.sendMessage("lw-7", "alice", "status", "v2", null, "corr-updated", null, null, null, null, null, null, null);

        assertEquals("corr-updated", overwrite.correlationId(),
                "LAST_WRITE overwrite should replace correlationId, not retain the original");
        CheckResult messages = helper.checkMessages("lw-7", 0L, 10, null, null, null);
        assertEquals("corr-updated", messages.get(0).correlationId());
    }

    @Test
    @TestTransaction
    void appendChannelAllowsMultipleSendersUnaffected() {
        helper.createChannel("append-lw", "APPEND channel", "APPEND", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        DispatchResult m1 = helper.sendMessage("append-lw", "alice", "status", "first", null, null, null, null, null, null, null, null, null);
        DispatchResult m2 = helper.sendMessage("append-lw", "bob", "status", "second", null, null, null, null, null, null, null, null, null);

        // APPEND creates distinct messages, different IDs
        assertNotEquals(m1.messageId(), m2.messageId(),
                "APPEND channel should not apply LAST_WRITE logic");

        CheckResult messages = helper.checkMessages("append-lw", 0L, 10, null, null, null);
        assertEquals(2, messages.size());
    }
}
