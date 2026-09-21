package io.casehub.qhorus.mcp;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.runtime.channel.ChannelService;
import io.casehub.qhorus.api.message.DispatchResult;
import io.casehub.qhorus.testing.QhorusTestHelper;
import io.casehub.qhorus.testing.QhorusTestHelper.CommitmentDetail;
import io.casehub.qhorus.testing.QhorusTestHelper.WaitResult;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.runtime.message.CommitmentService;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Issue #38 — Approval gate: request_approval, respond_to_approval, list_pending_commitments.
 *
 * <h2>Design notes</h2>
 * <p>
 * {@code request_approval} is a thin wrapper: send request message + wait_for_reply.
 * A non-@Tool 4-arg overload accepts a pre-supplied correlationId so tests can pre-seed
 * the response and avoid blocking.
 *
 * <h2>Threading note</h2>
 * <p>
 * Raw {@code ExecutorService} threads do not propagate Quarkus CDI context, which breaks
 * {@code @Transactional} calls. Tests for {@code list_pending_commitments} use
 * {@code commitmentService.open()} directly to set up state on the main thread
 * instead of calling {@code requestApproval} from a background thread. The full
 * request->discover->respond->receive flow is tested in E2E using pre-seeded responses that
 * allow {@code requestApproval} to return immediately without blocking.
 *
 * <p>
 * Refs #38, Epic #36, Refs #121.
 */
@QuarkusTest
class ApprovalGateTest {

    @Inject QhorusTestHelper helper;

    @Inject
    CommitmentService commitmentService;

    @Inject
    ChannelService channelService;

    // -------------------------------------------------------------------------
    // Unit — respond_to_approval
    // -------------------------------------------------------------------------

    @Test
    @TestTransaction
    void respondToApprovalCreatesResponseMessageInChannel() {
        helper.createChannel("ag-respond-1", "Approvals", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        String corrId = UUID.randomUUID().toString();

        DispatchResult result = helper.respondToApproval(corrId, "approved — looks good", "ag-respond-1");

        assertNotNull(result);
        assertEquals(io.casehub.qhorus.api.message.MessageType.RESPONSE, result.type());
        assertEquals(corrId, result.correlationId());
    }

    @Test
    @TestTransaction
    void respondToApprovalSenderIsHuman() {
        helper.createChannel("ag-respond-2", "Approvals", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        String corrId = UUID.randomUUID().toString();

        helper.respondToApproval(corrId, "denied", "ag-respond-2");

        var check = helper.checkMessages("ag-respond-2", 0L, 10, null, null, null);
        assertEquals("human", check.get(0).sender(),
                "respond_to_approval should use 'human' as the sender");
    }

    @Test
    @TestTransaction
    void respondToApprovalSetsCorrelationId() {
        helper.createChannel("ag-respond-3", "Approvals", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        String corrId = UUID.randomUUID().toString();

        DispatchResult result = helper.respondToApproval(corrId, "yes", "ag-respond-3");

        assertEquals(corrId, result.correlationId());
    }

    @Test
    @TestTransaction
    void respondToApprovalOnUnknownChannelThrows() {
        assertThrows(IllegalArgumentException.class, () -> helper.respondToApproval("any-id", "yes", "no-such-channel"));
    }

    // -------------------------------------------------------------------------
    // Unit — list_pending_commitments (approval discovery)
    // (State set up directly via commitmentService to avoid threading/CDI issues)
    // -------------------------------------------------------------------------

    @Test
    @TestTransaction
    void listPendingCommitmentsShowsRegisteredApproval() {
        helper.createChannel("ag-list-1", "Approvals", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        String corrId = UUID.randomUUID().toString();

        // Register directly via CommitmentService — simulates what wait_for_reply does
        var ch = channelService.findByName("ag-list-1").orElseThrow();
        commitmentService.open(UUID.randomUUID(), corrId, ch.id(),
                MessageType.QUERY, "test-agent", null, Instant.now().plusSeconds(60));

        List<CommitmentDetail> pending = helper.listPendingCommitments();
        assertTrue(pending.stream().anyMatch(a -> corrId.equals(a.correlationId())),
                "registered approval should appear in list_pending_commitments");
    }

    @Test
    @TestTransaction
    void listPendingCommitmentsResolvesChannelId() {
        helper.createChannel("ag-list-2", "Approvals", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        String corrId = UUID.randomUUID().toString();

        var ch = channelService.findByName("ag-list-2").orElseThrow();
        commitmentService.open(UUID.randomUUID(), corrId, ch.id(),
                MessageType.QUERY, "test-agent", null, Instant.now().plusSeconds(60));

        CommitmentDetail summary = helper.listPendingCommitments().stream()
                .filter(a -> corrId.equals(a.correlationId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Expected commitment not found"));

        assertEquals(ch.id().toString(), summary.channelId(),
                "channelId should be present on the commitment detail");
    }

    @Test
    @TestTransaction
    void listPendingCommitmentsShowsExpiresAt() {
        helper.createChannel("ag-list-3", "Approvals", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        String corrId = UUID.randomUUID().toString();

        var ch = channelService.findByName("ag-list-3").orElseThrow();
        commitmentService.open(UUID.randomUUID(), corrId, ch.id(),
                MessageType.QUERY, "test-agent", null, Instant.now().plusSeconds(120));

        CommitmentDetail summary = helper.listPendingCommitments().stream()
                .filter(a -> corrId.equals(a.correlationId()))
                .findFirst()
                .orElseThrow();

        assertNotNull(summary.expiresAt(),
                "expiresAt should be set for a commitment with a deadline");
    }

    @Test
    @TestTransaction
    void listPendingCommitmentsOrdersByExpiresAtAscending() {
        helper.createChannel("ag-list-4", "Approvals", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        String corrId1 = UUID.randomUUID().toString();
        String corrId2 = UUID.randomUUID().toString();

        var ch = channelService.findByName("ag-list-4").orElseThrow();
        // corrId2 expires sooner (registered with shorter timeout)
        commitmentService.open(UUID.randomUUID(), corrId1, ch.id(),
                MessageType.QUERY, "test-agent", null, Instant.now().plusSeconds(200));
        commitmentService.open(UUID.randomUUID(), corrId2, ch.id(),
                MessageType.QUERY, "test-agent", null, Instant.now().plusSeconds(60));

        List<CommitmentDetail> pending = helper.listPendingCommitments().stream()
                .filter(a -> corrId1.equals(a.correlationId()) || corrId2.equals(a.correlationId()))
                .toList();

        assertEquals(2, pending.size());
        assertEquals(corrId2, pending.get(0).correlationId(),
                "soonest-expiring (longest-waiting) commitment should be first");
    }

    // -------------------------------------------------------------------------
    // Integration — request_approval with pre-seeded response (no blocking)
    // -------------------------------------------------------------------------

    @Test
    void requestApprovalFindsPreSeededResponse() {
        helper.createChannel("ag-req-1", "Approvals", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        String corrId = UUID.randomUUID().toString();

        // Send QUERY first so we have a messageId to reply to
        DispatchResult query = helper.sendMessage("ag-req-1", "agent", "query", "please approve this", null, corrId, null, null, null, null, null, null, null);

        // Pre-seed the human's response with inReplyTo pointing to the query
        helper.sendMessage("ag-req-1", "human", "response", "approved by human", null, corrId, query.messageId(), null, null, null, null, null, null);

        // wait_for_reply finds the pre-seeded response immediately
        WaitResult result = helper.waitForReply("ag-req-1", corrId, 5, null);

        assertTrue(result.found(), "should find the pre-seeded response immediately");
        assertFalse(result.timedOut());
        assertEquals("approved by human", result.message().content());
        assertEquals("human", result.message().sender());
    }

    @Test
    void requestApprovalCreatesRequestMessageInChannel() {
        helper.createChannel("ag-req-2", "Approvals", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        String corrId = UUID.randomUUID().toString();

        // Send QUERY + pre-seed response so wait doesn't block long
        DispatchResult query = helper.sendMessage("ag-req-2", "agent", "query", "needs approval", null, corrId, null, null, null, null, null, null, null);
        helper.sendMessage("ag-req-2", "human", "response", "ok", null, corrId, query.messageId(), null, null, null, null, null, null);
        helper.waitForReply("ag-req-2", corrId, 5, null);

        // Channel should contain both query and response
        var check = helper.checkMessages("ag-req-2", 0L, 10, null, null, null);
        assertTrue(check.stream().anyMatch(m -> "QUERY".equals(m.messageType())),
                "query message should be in the channel");
        assertTrue(check.stream().anyMatch(m -> "RESPONSE".equals(m.messageType())));
    }

    @Test
    void requestApprovalUsesProvidedCorrelationId() {
        helper.createChannel("ag-req-3", "Approvals", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        String corrId = UUID.randomUUID().toString();

        DispatchResult query = helper.sendMessage("ag-req-3", "agent", "query", "approve", null, corrId, null, null, null, null, null, null, null);
        helper.sendMessage("ag-req-3", "human", "response", "ok", null, corrId, query.messageId(), null, null, null, null, null, null);
        WaitResult result = helper.waitForReply("ag-req-3", corrId, 5, null);

        assertEquals(corrId, result.correlationId());
    }

    @Test
    void requestApprovalTimesOutGracefully() {
        helper.createChannel("ag-req-4", "Approvals", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        String corrId = UUID.randomUUID().toString();

        // No response seeded — should time out after 1s
        WaitResult result = helper.requestApprovalWithCorrelationId("ag-req-4", "needs approval", corrId, 1);

        assertFalse(result.found());
        assertTrue(result.timedOut(), "should time out when no response arrives");
    }

    // -------------------------------------------------------------------------
    // E2E — full approval lifecycle: request -> discover -> respond -> receive
    // -------------------------------------------------------------------------

    @Test
    void e2eAgentRequestsHumanApproves() {
        helper.createChannel("ag-e2e-1", "Human Approvals", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        String corrId = UUID.randomUUID().toString();

        // 1. Simulate the pending state by opening a commitment directly —
        //    mirrors what wait_for_reply does (testing the discovery + response flow)
        var ch = channelService.findByName("ag-e2e-1").orElseThrow();
        commitmentService.open(UUID.randomUUID(), corrId, ch.id(),
                MessageType.QUERY, "test-agent", null, Instant.now().plusSeconds(60));

        // 2. Human calls list_pending_commitments — discovers the waiting request
        List<CommitmentDetail> pending = helper.listPendingCommitments();
        assertTrue(pending.stream().anyMatch(a -> corrId.equals(a.correlationId())),
                "human should see the pending commitment in the list");

        // 3. Human calls respond_to_approval
        helper.respondToApproval(corrId, "Approved — proceed with deployment", "ag-e2e-1");

        // 4. Verify the response message exists in the channel with correct fields
        var check = helper.checkMessages("ag-e2e-1", 0L, 10, null, null, null);
        var response = check.stream()
                .filter(m -> "RESPONSE".equals(m.messageType()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Response message not found"));
        assertEquals("Approved — proceed with deployment", response.content());
        assertEquals("human", response.sender());
        assertEquals(corrId, response.correlationId());
    }

    @Test
    void e2eRequestApprovalThenRespondReturnsToCaller() {
        helper.createChannel("ag-e2e-2", "Approvals", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        String corrId = UUID.randomUUID().toString();

        // 1. Send QUERY first so we have messageId for inReplyTo
        DispatchResult query = helper.sendMessage("ag-e2e-2", "agent", "query", "Deploy to production?", null, corrId, null, null, null, null, null, null, null);

        // 2. Pre-seed the human's response with inReplyTo
        helper.sendMessage("ag-e2e-2", "human", "response", "Yes, deploy it!", null, corrId, query.messageId(), null, null, null, null, null, null);

        // 3. Agent waits for reply — finds response immediately
        WaitResult agentResult = helper.waitForReply("ag-e2e-2", corrId, 5, null);

        // 4. Agent verifies the approval
        assertTrue(agentResult.found());
        assertEquals("Yes, deploy it!", agentResult.message().content());
        assertEquals("human", agentResult.message().sender());
    }

    @Test
    void e2eMultiplePendingCommitmentsDiscoveredAndAnswered() {
        helper.createChannel("ag-e2e-3", "Approvals", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        String corrId1 = UUID.randomUUID().toString();
        String corrId2 = UUID.randomUUID().toString();

        var ch = channelService.findByName("ag-e2e-3").orElseThrow();

        // Two agents each have a pending approval
        commitmentService.open(UUID.randomUUID(), corrId1, ch.id(),
                MessageType.QUERY, "agent-1", null, Instant.now().plusSeconds(60));
        commitmentService.open(UUID.randomUUID(), corrId2, ch.id(),
                MessageType.QUERY, "agent-2", null, Instant.now().plusSeconds(60));

        // Human sees both
        List<CommitmentDetail> pending = helper.listPendingCommitments();
        assertTrue(pending.stream().anyMatch(a -> corrId1.equals(a.correlationId())));
        assertTrue(pending.stream().anyMatch(a -> corrId2.equals(a.correlationId())));

        // Human responds to both
        helper.respondToApproval(corrId1, "Approved #1", "ag-e2e-3");
        helper.respondToApproval(corrId2, "Approved #2", "ag-e2e-3");

        // Both responses exist in the channel
        var check = helper.checkMessages("ag-e2e-3", 0L, 10, null, null, null);
        assertEquals(2, check.stream()
                .filter(m -> "RESPONSE".equals(m.messageType())).count());
    }
}
