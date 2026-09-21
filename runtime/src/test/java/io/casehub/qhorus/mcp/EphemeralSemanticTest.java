package io.casehub.qhorus.mcp;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehub.qhorus.testing.QhorusTestHelper;
import io.casehub.qhorus.testing.QhorusTestHelper.CheckResult;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class EphemeralSemanticTest {

    @Inject QhorusTestHelper helper;

    @Test
    @TestTransaction
    void ephemeralMessageDeliveredOnFirstRead() {
        helper.createChannel("eph-1", "EPHEMERAL channel", "EPHEMERAL", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("eph-1", "alice", "status", "routing hint", null, null, null, null, null, null, null, null, null);

        CheckResult result = helper.checkMessages("eph-1", 0L, 10, null, null, null);

        assertEquals(1, result.size());
        assertEquals("routing hint", result.get(0).content());
    }

    @Test
    @TestTransaction
    void ephemeralSecondReadReturnsEmpty() {
        helper.createChannel("eph-2", "EPHEMERAL channel", "EPHEMERAL", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("eph-2", "alice", "status", "transient context", null, null, null, null, null, null, null, null, null);

        helper.checkMessages("eph-2", 0L, 10, null, null, null); // First read — consumes

        CheckResult second = helper.checkMessages("eph-2", 0L, 10, null, null, null);
        assertTrue(second.isEmpty(),
                "EPHEMERAL messages should be gone after first read");
    }

    @Test
    @TestTransaction
    void ephemeralNewWriteAfterReadIsDeliveredOnNextRead() {
        helper.createChannel("eph-3", "EPHEMERAL channel", "EPHEMERAL", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("eph-3", "alice", "status", "first hint", null, null, null, null, null, null, null, null, null);
        helper.checkMessages("eph-3", 0L, 10, null, null, null); // Consume first

        helper.sendMessage("eph-3", "alice", "status", "second hint", null, null, null, null, null, null, null, null, null);
        CheckResult result = helper.checkMessages("eph-3", 0L, 10, null, null, null);

        assertEquals(1, result.size());
        assertEquals("second hint", result.get(0).content());
    }

    @Test
    @TestTransaction
    void ephemeralMultipleMessagesAllConsumedOnFirstRead() {
        helper.createChannel("eph-4", "EPHEMERAL channel", "EPHEMERAL", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("eph-4", "alice", "status", "hint-1", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("eph-4", "bob", "status", "hint-2", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("eph-4", "carol", "status", "hint-3", null, null, null, null, null, null, null, null, null);

        CheckResult first = helper.checkMessages("eph-4", 0L, 10, null, null, null);
        assertEquals(3, first.size());

        CheckResult second = helper.checkMessages("eph-4", 0L, 10, null, null, null);
        assertTrue(second.isEmpty());
    }

    @Test
    @TestTransaction
    void ephemeralLimitOnlyDeletesDeliveredMessages() {
        helper.createChannel("eph-5", "EPHEMERAL channel", "EPHEMERAL", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("eph-5", "alice", "status", "msg-1", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("eph-5", "alice", "status", "msg-2", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("eph-5", "alice", "status", "msg-3", null, null, null, null, null, null, null, null, null);

        // Read with limit=2 — only the first two should be delivered and deleted
        CheckResult first = helper.checkMessages("eph-5", 0L, 2, null, null, null);
        assertEquals(2, first.size(), "limit=2 should deliver 2 messages");

        // Third message was NOT delivered, so it should still be available
        CheckResult second = helper.checkMessages("eph-5", 0L, 10, null, null, null);
        assertEquals(1, second.size(),
                "The undelivered third EPHEMERAL message should survive a partial first read");
        assertEquals("msg-3", second.get(0).content());
    }

    @Test
    @TestTransaction
    void appendChannelUnaffectedByEphemeralLogic() {
        helper.createChannel("append-eph", "APPEND channel", "APPEND", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("append-eph", "alice", "status", "persistent", null, null, null, null, null, null, null, null, null);
        helper.checkMessages("append-eph", 0L, 10, null, null, null);

        // APPEND messages survive reads
        CheckResult second = helper.checkMessages("append-eph", 0L, 10, null, null, null);
        assertEquals(1, second.size(),
                "APPEND channel messages should not be deleted on read");
    }
}
