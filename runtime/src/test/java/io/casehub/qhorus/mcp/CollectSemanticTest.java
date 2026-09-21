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
class CollectSemanticTest {

    @Inject QhorusTestHelper helper;

    @Test
    @TestTransaction
    void collectDeliversAllContributionsAtomically() {
        helper.createChannel("col-1", "COLLECT channel", "COLLECT", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("col-1", "alice", "status", "alice's finding", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("col-1", "bob", "status", "bob's finding", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("col-1", "carol", "status", "carol's finding", null, null, null, null, null, null, null, null, null);

        CheckResult result = helper.checkMessages("col-1", 0L, 10, null, null, null);

        assertEquals(3, result.size(),
                "COLLECT should deliver all accumulated messages");
    }

    @Test
    @TestTransaction
    void collectClearsChannelAfterDelivery() {
        helper.createChannel("col-2", "COLLECT channel", "COLLECT", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("col-2", "alice", "status", "contribution", null, null, null, null, null, null, null, null, null);
        helper.checkMessages("col-2", 0L, 10, null, null, null); // Deliver and clear

        CheckResult second = helper.checkMessages("col-2", 0L, 10, null, null, null);
        assertTrue(second.isEmpty(),
                "COLLECT channel should be empty after delivery");
    }

    @Test
    @TestTransaction
    void collectIgnoresAfterIdCursorDeliversAll() {
        helper.createChannel("col-3", "COLLECT channel", "COLLECT", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        DispatchResult m1 = helper.sendMessage("col-3", "alice", "status", "first", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("col-3", "bob", "status", "second", null, null, null, null, null, null, null, null, null);

        // Even with afterId past the first message, COLLECT delivers everything pending
        CheckResult result = helper.checkMessages("col-3", m1.messageId(), 10, null, null, null);

        assertEquals(2, result.size(),
                "COLLECT should deliver all messages regardless of afterId cursor");
    }

    @Test
    @TestTransaction
    void collectNewCycleAfterClear() {
        helper.createChannel("col-4", "COLLECT channel", "COLLECT", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("col-4", "alice", "status", "cycle-1 data", null, null, null, null, null, null, null, null, null);
        helper.checkMessages("col-4", 0L, 10, null, null, null); // Clear cycle 1

        // New writes start cycle 2
        helper.sendMessage("col-4", "bob", "status", "cycle-2 data", null, null, null, null, null, null, null, null, null);
        CheckResult result = helper.checkMessages("col-4", 0L, 10, null, null, null);

        assertEquals(1, result.size());
        assertEquals("cycle-2 data", result.get(0).content());
    }

    @Test
    @TestTransaction
    void collectExcludesEventMessages() {
        helper.createChannel("col-5", "COLLECT channel", "COLLECT", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("col-5", "alice", "status", "visible", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("col-5", "system", "event", null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("col-5", "bob", "status", "also visible", null, null, null, null, null, null, null, null, null);

        CheckResult result = helper.checkMessages("col-5", 0L, 10, null, null, null);

        assertEquals(2, result.size(),
                "COLLECT should exclude EVENT messages from delivery");
        assertTrue(result.stream().noneMatch(m -> "event".equalsIgnoreCase(m.messageType())));
    }

    @Test
    @TestTransaction
    void appendChannelUnaffectedByCollectLogic() {
        helper.createChannel("append-col", "APPEND channel", "APPEND", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("append-col", "alice", "status", "persistent", null, null, null, null, null, null, null, null, null);
        helper.checkMessages("append-col", 0L, 10, null, null, null);

        CheckResult second = helper.checkMessages("append-col", 0L, 10, null, null, null);
        assertEquals(1, second.size(),
                "APPEND channel messages should not be cleared on read");
    }
}
