package io.casehub.qhorus.mcp;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehub.qhorus.api.message.DispatchResult;
import io.casehub.qhorus.testing.QhorusTestHelper;
import io.casehub.qhorus.testing.QhorusTestHelper.CheckResult;
import java.util.List;
import io.casehub.qhorus.api.message.Message;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Issue #28 — Add target field to Message: schema, storage, and format validation.
 *
 * <p>
 * Verifies that:
 * <ul>
 * <li>send_message accepts a target in instance:*, capability:*, role:* format</li>
 * <li>null/blank target is stored as null (backward-compat: all readers see the message)</li>
 * <li>malformed targets are rejected with IllegalArgumentException</li>
 * <li>target flows through to DispatchResult and MessageSummary</li>
 * </ul>
 *
 * <p>
 * No dispatch logic — just storage and validation. Refs #28, Epic #27.
 */
@QuarkusTest
class TargetFieldStorageTest {

    @Inject QhorusTestHelper helper;

    // -------------------------------------------------------------------------
    // Null / blank target — backward compat
    // -------------------------------------------------------------------------

    @Test
    @TestTransaction
    void nullTargetIsStoredAsNullInResult() {
        helper.createChannel("tgt-null-1", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        DispatchResult result = helper.sendMessage("tgt-null-1", "alice", "status", "msg", null, null, null, null, null, null, null, null, null);
        assertNull(result.target(), "null target should be stored and returned as null");
    }

    @Test
    @TestTransaction
    void blankTargetIsTreatedAsNull() {
        helper.createChannel("tgt-blank-1", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        DispatchResult result = helper.sendMessage("tgt-blank-1", "alice", "status", "msg", null, null, null, null, "", null, null, null, null);
        assertNull(result.target(), "blank target should be normalised to null");
    }

    // -------------------------------------------------------------------------
    // Valid target formats — stored and returned
    // -------------------------------------------------------------------------

    @Test
    @TestTransaction
    void instanceTargetIsStoredAndReturnedInResult() {
        helper.createChannel("tgt-inst-1", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        DispatchResult result = helper.sendMessage("tgt-inst-1", "alice", "status", "msg", null, null, null, null, "instance:bob", null, null, null, null);
        assertEquals("instance:bob", result.target());
    }

    @Test
    @TestTransaction
    void capabilityTargetIsStoredAndReturnedInResult() {
        helper.createChannel("tgt-cap-1", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        DispatchResult result = helper.sendMessage("tgt-cap-1", "alice", "command", "msg", null, null, null, null, "capability:code-review", null, null, null, null);
        assertEquals("capability:code-review", result.target());
    }

    @Test
    @TestTransaction
    void roleTargetIsStoredAndReturnedInResult() {
        helper.createChannel("tgt-role-1", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        DispatchResult result = helper.sendMessage("tgt-role-1", "alice", "command", "msg", null, null, null, null, "role:reviewer", null, null, null, null);
        assertEquals("role:reviewer", result.target());
    }

    // -------------------------------------------------------------------------
    // Target appears in MessageSummary via check_messages
    // -------------------------------------------------------------------------

    @Test
    @TestTransaction
    void targetFlowsThroughToCheckMessagesMessageSummary() {
        helper.createChannel("tgt-chk-1", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("tgt-chk-1", "alice", "status", "targeted msg", null, null, null, null, "instance:bob", null, null, null, null);

        var check = helper.checkMessages("tgt-chk-1", 0L, 10, null, null, null);
        assertFalse(check.isEmpty());
        var summary = check.get(0);
        assertEquals("instance:bob", summary.target(),
                "target should be present in MessageSummary from check_messages");
    }

    @Test
    @TestTransaction
    void nullTargetFlowsThroughToCheckMessagesMessageSummary() {
        helper.createChannel("tgt-chk-2", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("tgt-chk-2", "alice", "status", "broadcast msg", null, null, null, null, null, null, null, null, null);

        var check = helper.checkMessages("tgt-chk-2", 0L, 10, null, null, null);
        assertFalse(check.isEmpty());
        assertNull(check.get(0).target(),
                "null target should be null in MessageSummary");
    }

    // -------------------------------------------------------------------------
    // Malformed target — format validation
    // -------------------------------------------------------------------------

    @Test
    @TestTransaction
    void unknownPrefixThrowsIllegalArgument() {
        helper.createChannel("tgt-bad-1", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> helper.sendMessage("tgt-bad-1", "alice", "status", "msg", null, null, null, null, "garbage:foo", null, null, null, null));
        assertTrue(ex.getMessage().contains("garbage:foo"),
                "Error message should identify the invalid target value");
    }

    @Test
    @TestTransaction
    void bareWordWithoutPrefixThrowsIllegalArgument() {
        helper.createChannel("tgt-bad-2", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        assertThrows(IllegalArgumentException.class, () -> helper.sendMessage("tgt-bad-2", "alice", "status", "msg", null, null, null, null, "alice", null, null, null, null));
    }

    @Test
    @TestTransaction
    void prefixWithoutValueThrowsIllegalArgument() {
        helper.createChannel("tgt-bad-3", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        // "instance:" with no actual id
        assertThrows(IllegalArgumentException.class, () -> helper.sendMessage("tgt-bad-3", "alice", "status", "msg", null, null, null, null, "instance:", null, null, null, null));
    }

    // -------------------------------------------------------------------------
    // Backward-compat: existing 7-arg overload still works (no target)
    // -------------------------------------------------------------------------

    @Test
    @TestTransaction
    void sevenArgOverloadStillWorksWithNullTarget() {
        helper.createChannel("tgt-compat-1", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        // This calls the non-@Tool 7-arg overload — must still compile and succeed
        DispatchResult result = helper.sendMessage("tgt-compat-1", "alice", "status", "msg", null, null, null, null, null, null, null, null, null);
        assertNull(result.target(), "7-arg overload should default target to null");
    }
}
