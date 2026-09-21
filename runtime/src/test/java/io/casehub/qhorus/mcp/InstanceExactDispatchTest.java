package io.casehub.qhorus.mcp;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehub.qhorus.api.message.DispatchResult;
import io.casehub.qhorus.testing.QhorusTestHelper;
import io.casehub.qhorus.testing.QhorusTestHelper.CheckResult;
import io.casehub.qhorus.testing.QhorusTestHelper.MessageSummary;
import io.casehub.qhorus.api.message.Message;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Issue #29 — Implement instance-exact dispatch on read side.
 *
 * <p>
 * Verifies that when {@code reader_instance_id} is provided to check_messages,
 * get_replies, and search_messages, only messages with {@code target IS NULL} or
 * {@code target = 'instance:{reader_instance_id}'} are returned. Omitting
 * {@code reader_instance_id} preserves existing broadcast-to-all behaviour.
 *
 * <p>
 * Refs #29, Epic #27.
 */
@QuarkusTest
class InstanceExactDispatchTest {

    @Inject QhorusTestHelper helper;

    // =========================================================================
    // check_messages — APPEND channel
    // =========================================================================

    @Test
    @TestTransaction
    void broadcastMessageVisibleToReaderWithReaderInstanceId() {
        helper.createChannel("ied-ap-1", "Test", "APPEND", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("ied-ap-1", "alice", "status", "broadcast", null, null, null, null, null, null, null, null, null);

        CheckResult result = helper.checkMessages("ied-ap-1", 0L, 10, null, "bob", null);
        assertEquals(1, result.size(), "broadcast message should be visible to any reader");
    }

    @Test
    @TestTransaction
    void instanceTargetedMessageVisibleToCorrectReader() {
        helper.createChannel("ied-ap-2", "Test", "APPEND", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("ied-ap-2", "alice", "command", "for bob", null, null, null, null, "instance:bob", null, null, null, null);

        CheckResult result = helper.checkMessages("ied-ap-2", 0L, 10, null, "bob", null);
        assertEquals(1, result.size());
        assertEquals("instance:bob", result.get(0).target());
    }

    @Test
    @TestTransaction
    void instanceTargetedMessageHiddenFromOtherReader() {
        helper.createChannel("ied-ap-3", "Test", "APPEND", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("ied-ap-3", "alice", "command", "for bob", null, null, null, null, "instance:bob", null, null, null, null);

        CheckResult result = helper.checkMessages("ied-ap-3", 0L, 10, null, "charlie", null);
        assertTrue(result.isEmpty(), "message targeted at bob should be invisible to charlie");
    }

    @Test
    @TestTransaction
    void omittingReaderInstanceIdShowsAllMessagesIncludingTargeted() {
        helper.createChannel("ied-ap-4", "Test", "APPEND", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("ied-ap-4", "alice", "status", "broadcast", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("ied-ap-4", "alice", "command", "for bob", null, null, null, null, "instance:bob", null, null, null, null);

        // No reader_instance_id → backward-compat: all messages visible
        CheckResult result = helper.checkMessages("ied-ap-4", 0L, 10, null, null, null);
        assertEquals(2, result.size(), "omitting reader shows all messages");
    }

    @Test
    @TestTransaction
    void mixedBroadcastAndTargetedMessagesFilteredCorrectly() {
        helper.createChannel("ied-ap-5", "Test", "APPEND", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("ied-ap-5", "alice", "status", "broadcast", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("ied-ap-5", "alice", "command", "for bob", null, null, null, null, "instance:bob", null, null, null, null);
        helper.sendMessage("ied-ap-5", "alice", "command", "for charlie", null, null, null, null, "instance:charlie", null, null, null, null);

        CheckResult bobView = helper.checkMessages("ied-ap-5", 0L, 10, null, "bob", null);
        assertEquals(2, bobView.size(), "bob sees broadcast + his targeted message");

        CheckResult charlieView = helper.checkMessages("ied-ap-5", 0L, 10, null, "charlie", null);
        assertEquals(2, charlieView.size(), "charlie sees broadcast + her targeted message");
    }

    // =========================================================================
    // check_messages — EPHEMERAL channel
    // =========================================================================

    @Test
    @TestTransaction
    void instanceTargetedEphemeralMessageVisibleToCorrectReaderAndConsumed() {
        helper.createChannel("ied-eph-1", "Test", "EPHEMERAL", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("ied-eph-1", "alice", "command", "for bob", null, null, null, null, "instance:bob", null, null, null, null);

        // Bob reads it — should be delivered and deleted
        CheckResult bobRead = helper.checkMessages("ied-eph-1", 0L, 10, null, "bob", null);
        assertEquals(1, bobRead.size());

        // Bob reads again — gone
        CheckResult bobReadAgain = helper.checkMessages("ied-eph-1", 0L, 10, null, "bob", null);
        assertTrue(bobReadAgain.isEmpty(), "EPHEMERAL message consumed after delivery to bob");
    }

    @Test
    @TestTransaction
    void instanceTargetedEphemeralMessageNotConsumedByWrongReader() {
        helper.createChannel("ied-eph-2", "Test", "EPHEMERAL", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("ied-eph-2", "alice", "command", "for bob", null, null, null, null, "instance:bob", null, null, null, null);

        // Charlie reads — should see nothing and NOT consume bob's message
        CheckResult charlieRead = helper.checkMessages("ied-eph-2", 0L, 10, null, "charlie", null);
        assertTrue(charlieRead.isEmpty(), "charlie should not see bob's targeted message");

        // Bob reads — message should still be there
        CheckResult bobRead = helper.checkMessages("ied-eph-2", 0L, 10, null, "bob", null);
        assertEquals(1, bobRead.size(), "bob's message must still exist after charlie polled");
    }

    // =========================================================================
    // get_replies
    // =========================================================================

    @Test
    @TestTransaction
    void instanceTargetedReplyVisibleToCorrectReader() {
        helper.createChannel("ied-rep-1", "Test", "APPEND", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        DispatchResult parent = helper.sendMessage("ied-rep-1", "alice", "query", "question", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("ied-rep-1", "bob", "response", "answer for alice", null, parent.correlationId(), parent.messageId(), null, "instance:alice", null, null, null, null);

        List<MessageSummary> replies = helper.getReplies(parent.messageId(), "alice", null, null);
        assertEquals(1, replies.size(), "alice should see the reply targeted at her");
    }

    @Test
    @TestTransaction
    void instanceTargetedReplyHiddenFromOtherReader() {
        helper.createChannel("ied-rep-2", "Test", "APPEND", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        DispatchResult parent = helper.sendMessage("ied-rep-2", "alice", "query", "question", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("ied-rep-2", "bob", "response", "answer for alice", null, parent.correlationId(), parent.messageId(), null, "instance:alice", null, null, null, null);

        List<MessageSummary> replies = helper.getReplies(parent.messageId(), "charlie", null, null);
        assertTrue(replies.isEmpty(), "charlie should not see reply targeted at alice");
    }

    @Test
    @TestTransaction
    void omittingReaderFromGetRepliesShowsAllReplies() {
        helper.createChannel("ied-rep-3", "Test", "APPEND", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        DispatchResult parent = helper.sendMessage("ied-rep-3", "alice", "query", "question", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("ied-rep-3", "bob", "response", "broadcast reply", null, parent.correlationId(), parent.messageId(), null, null, null, null, null, null);
        helper.sendMessage("ied-rep-3", "bob", "response", "answer for alice", null, parent.correlationId(), parent.messageId(), null, "instance:alice", null, null, null, null);

        // No reader — backward compat, all replies visible
        List<MessageSummary> replies = helper.getReplies(parent.messageId(), null, null, null);
        assertEquals(2, replies.size());
    }

    // =========================================================================
    // search_messages
    // =========================================================================

    @Test
    @TestTransaction
    void instanceTargetedMessageFoundBySearchForCorrectReader() {
        helper.createChannel("ied-srch-1", "Test", "APPEND", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("ied-srch-1", "alice", "status", "secret for bob", null, null, null, null, "instance:bob", null, null, null, null);

        List<MessageSummary> results = helper.searchMessages("secret", "ied-srch-1", 10, "bob");
        assertEquals(1, results.size());
    }

    @Test
    @TestTransaction
    void instanceTargetedMessageNotFoundBySearchForOtherReader() {
        helper.createChannel("ied-srch-2", "Test", "APPEND", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("ied-srch-2", "alice", "status", "secret for bob", null, null, null, null, "instance:bob", null, null, null, null);

        List<MessageSummary> results = helper.searchMessages("secret", "ied-srch-2", 10, "charlie");
        assertTrue(results.isEmpty(), "charlie should not find bob's targeted message via search");
    }

    @Test
    @TestTransaction
    void omittingReaderFromSearchShowsAllMessages() {
        helper.createChannel("ied-srch-3", "Test", "APPEND", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("ied-srch-3", "alice", "status", "secret for bob", null, null, null, null, "instance:bob", null, null, null, null);
        helper.sendMessage("ied-srch-3", "alice", "status", "secret broadcast", null, null, null, null, null, null, null, null, null);

        // No reader — backward compat
        List<MessageSummary> results = helper.searchMessages("secret", "ied-srch-3", 10, null);
        assertEquals(2, results.size());
    }
}
