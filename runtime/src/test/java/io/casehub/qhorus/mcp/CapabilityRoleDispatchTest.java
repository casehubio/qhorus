package io.casehub.qhorus.mcp;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehub.qhorus.api.message.DispatchResult;
import io.casehub.qhorus.testing.QhorusTestHelper;
import io.casehub.qhorus.testing.QhorusTestHelper.MessageSummary;
import io.casehub.qhorus.api.message.Message;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Issue #30 — Implement capability and role dispatch on read side.
 *
 * <p>
 * The full target string is the capability tag stored in the Capability table:
 * <ul>
 * <li>{@code capability:code-review} → matches Capability.tag = {@code "capability:code-review"}</li>
 * <li>{@code role:reviewer} → matches Capability.tag = {@code "role:reviewer"}</li>
 * </ul>
 *
 * <p>
 * Agents register with qualified tags to be addressable:
 * {@code register("alice", "desc", ["capability:code-review", "role:reviewer"])}.
 *
 * <p>
 * Refs #30, Epic #27.
 */
@QuarkusTest
class CapabilityRoleDispatchTest {

    @Inject QhorusTestHelper helper;

    // =========================================================================
    // Capability dispatch — capability:X
    // =========================================================================

    @Test
    @TestTransaction
    void capabilityTargetedMessageVisibleToAgentWithMatchingTag() {
        helper.createChannel("crd-cap-1", "Test", "APPEND", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.register("reviewer-alice", "Code reviewer", List.of("capability:code-review"), null, null);
        helper.sendMessage("crd-cap-1", "sender", "command", "review this", null, null, null, null, "capability:code-review", null, null, null, null);

        var result = helper.checkMessages("crd-cap-1", 0L, 10, null, "reviewer-alice", null);
        assertEquals(1, result.size(),
                "agent with capability:code-review tag should see the message");
    }

    @Test
    @TestTransaction
    void capabilityTargetedMessageHiddenFromAgentWithoutMatchingTag() {
        helper.createChannel("crd-cap-2", "Test", "APPEND", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.register("python-bob", "Python dev", List.of("capability:python"), null, null);
        helper.sendMessage("crd-cap-2", "sender", "command", "review this", null, null, null, null, "capability:code-review", null, null, null, null);

        var result = helper.checkMessages("crd-cap-2", 0L, 10, null, "python-bob", null);
        assertTrue(result.isEmpty(),
                "agent without capability:code-review tag should not see the message");
    }

    @Test
    @TestTransaction
    void capabilityTargetedMessageHiddenFromUnregisteredReader() {
        helper.createChannel("crd-cap-3", "Test", "APPEND", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        // No register call — reader is unknown to the system
        helper.sendMessage("crd-cap-3", "sender", "command", "review this", null, null, null, null, "capability:code-review", null, null, null, null);

        var result = helper.checkMessages("crd-cap-3", 0L, 10, null, "ghost-reader", null);
        assertTrue(result.isEmpty(),
                "unregistered reader has no capability tags — should see nothing targeted");
    }

    @Test
    @TestTransaction
    void broadcastMessageVisibleToCapabilityReader() {
        helper.createChannel("crd-cap-4", "Test", "APPEND", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.register("cap-reader", "Agent", List.of("capability:code-review"), null, null);
        helper.sendMessage("crd-cap-4", "sender", "status", "broadcast", null, null, null, null, null, null, null, null, null);

        var result = helper.checkMessages("crd-cap-4", 0L, 10, null, "cap-reader", null);
        assertEquals(1, result.size(), "broadcast message visible to all readers");
    }

    // =========================================================================
    // Role dispatch — role:X (broadcast to all with that role)
    // =========================================================================

    @Test
    @TestTransaction
    void roleTargetedMessageVisibleToAllAgentsWithMatchingRoleTag() {
        helper.createChannel("crd-role-1", "Test", "APPEND", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.register("reviewer-1", "First reviewer", List.of("role:reviewer"), null, null);
        helper.register("reviewer-2", "Second reviewer", List.of("role:reviewer"), null, null);
        helper.sendMessage("crd-role-1", "sender", "command", "needs review", null, null, null, null, "role:reviewer", null, null, null, null);

        // Both reviewers independently see the message (broadcast)
        var r1 = helper.checkMessages("crd-role-1", 0L, 10, null, "reviewer-1", null);
        var r2 = helper.checkMessages("crd-role-1", 0L, 10, null, "reviewer-2", null);
        assertEquals(1, r1.size(), "reviewer-1 should see the role-targeted message");
        assertEquals(1, r2.size(), "reviewer-2 should also see the same message");
    }

    @Test
    @TestTransaction
    void roleTargetedMessageHiddenFromAgentWithoutRoleTag() {
        helper.createChannel("crd-role-2", "Test", "APPEND", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.register("non-reviewer", "Not a reviewer", List.of("capability:python"), null, null);
        helper.sendMessage("crd-role-2", "sender", "command", "needs review", null, null, null, null, "role:reviewer", null, null, null, null);

        var result = helper.checkMessages("crd-role-2", 0L, 10, null, "non-reviewer", null);
        assertTrue(result.isEmpty(),
                "agent without role:reviewer tag should not see the message");
    }

    // =========================================================================
    // Multi-tag agent — sees both capability and role targeted messages
    // =========================================================================

    @Test
    @TestTransaction
    void agentWithMultipleTagsSeesAllAddressedMessages() {
        helper.createChannel("crd-multi-1", "Test", "APPEND", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.register("full-alice", "Full-stack agent", List.of("capability:code-review", "role:reviewer"), null, null);
        helper.sendMessage("crd-multi-1", "sender", "command", "cap msg", null, null, null, null, "capability:code-review", null, null, null, null);
        helper.sendMessage("crd-multi-1", "sender", "command", "role msg", null, null, null, null, "role:reviewer", null, null, null, null);
        helper.sendMessage("crd-multi-1", "sender", "status", "broadcast", null, null, null, null, null, null, null, null, null);

        var result = helper.checkMessages("crd-multi-1", 0L, 10, null, "full-alice", null);
        assertEquals(3, result.size(),
                "agent with both tags should see cap-targeted, role-targeted, and broadcast messages");
    }

    // =========================================================================
    // Capability dispatch in get_replies
    // =========================================================================

    @Test
    @TestTransaction
    void capabilityTargetedReplyVisibleToAgentWithTag() {
        helper.createChannel("crd-rep-1", "Test", "APPEND", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.register("code-reviewer", "Code reviewer", List.of("capability:code-review"), null, null);
        DispatchResult parent = helper.sendMessage("crd-rep-1", "alice", "query", "question", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("crd-rep-1", "bob", "response", "answer", null, parent.correlationId(), parent.messageId(), null, "capability:code-review", null, null, null, null);

        List<MessageSummary> replies = helper.getReplies(parent.messageId(), "code-reviewer", null, null);
        assertEquals(1, replies.size(), "code reviewer should see capability-targeted reply");
    }

    @Test
    @TestTransaction
    void capabilityTargetedReplyHiddenFromAgentWithoutTag() {
        helper.createChannel("crd-rep-2", "Test", "APPEND", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.register("non-reviewer", "Not a reviewer", List.of("capability:python"), null, null);
        DispatchResult parent = helper.sendMessage("crd-rep-2", "alice", "query", "question", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("crd-rep-2", "bob", "response", "answer", null, parent.correlationId(), parent.messageId(), null, "capability:code-review", null, null, null, null);

        List<MessageSummary> replies = helper.getReplies(parent.messageId(), "non-reviewer", null, null);
        assertTrue(replies.isEmpty(), "agent without capability:code-review should not see the reply");
    }

    // =========================================================================
    // Capability dispatch in search_messages
    // =========================================================================

    @Test
    @TestTransaction
    void capabilityTargetedMessageFoundBySearchForAgentWithTag() {
        helper.createChannel("crd-srch-1", "Test", "APPEND", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.register("searcher", "Code reviewer", List.of("capability:code-review"), null, null);
        helper.sendMessage("crd-srch-1", "sender", "command", "searchable review request", null, null, null, null, "capability:code-review", null, null, null, null);

        List<MessageSummary> results = helper.searchMessages("searchable", "crd-srch-1", 10, "searcher");
        assertEquals(1, results.size());
    }

    @Test
    @TestTransaction
    void capabilityTargetedMessageNotFoundBySearchForAgentWithoutTag() {
        helper.createChannel("crd-srch-2", "Test", "APPEND", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.register("non-searcher", "Python dev", List.of("capability:python"), null, null);
        helper.sendMessage("crd-srch-2", "sender", "command", "searchable review request", null, null, null, null, "capability:code-review", null, null, null, null);

        List<MessageSummary> results = helper.searchMessages("searchable", "crd-srch-2", 10, "non-searcher");
        assertTrue(results.isEmpty());
    }
}
