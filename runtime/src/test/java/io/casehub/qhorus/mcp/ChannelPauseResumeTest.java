package io.casehub.qhorus.mcp;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehub.qhorus.api.channel.ChannelDetail;
import io.casehub.qhorus.testing.QhorusTestHelper;
import io.casehub.qhorus.testing.QhorusTestHelper.CheckResult;
import java.util.List;
import io.casehub.qhorus.api.message.Message;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Issue #37 — Channel pause/resume: pause_channel and resume_channel MCP helper.
 *
 * <p>
 * Three levels of testing:
 * <ul>
 * <li>Unit: pause/resume semantics, idempotency, paused field on ChannelDetail</li>
 * <li>Integration: full pause→fail→resume→succeed cycle</li>
 * <li>E2E: agents cannot post to paused channel; can again after resume</li>
 * </ul>
 *
 * <p>
 * Refs #37, Epic #36.
 */
@QuarkusTest
class ChannelPauseResumeTest {

    @Inject QhorusTestHelper helper;

    // -------------------------------------------------------------------------
    // Unit — pause semantics
    // -------------------------------------------------------------------------

    @Test
    @TestTransaction
    void newChannelIsNotPaused() {
        ChannelDetail detail = helper.createChannel("pr-new-1", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        assertFalse(detail.paused(), "newly created channel should not be paused");
    }

    @Test
    @TestTransaction
    void pauseChannelSetsPausedTrue() {
        helper.createChannel("pr-pause-1", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        ChannelDetail detail = helper.pauseChannel("pr-pause-1", null);
        assertTrue(detail.paused(), "channel should be paused after pause_channel");
    }

    @Test
    @TestTransaction
    void pauseChannelIsIdempotent() {
        helper.createChannel("pr-pause-2", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.pauseChannel("pr-pause-2", null);
        // Second call must not throw
        ChannelDetail detail = helper.pauseChannel("pr-pause-2", null);
        assertTrue(detail.paused(), "channel should still be paused after second pause call");
    }

    @Test
    @TestTransaction
    void pauseUnknownChannelThrowsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> helper.pauseChannel("no-such-channel", null), "pausing an unknown channel should throw IllegalArgumentException");
    }

    // -------------------------------------------------------------------------
    // Unit — resume semantics
    // -------------------------------------------------------------------------

    @Test
    @TestTransaction
    void resumeChannelSetsPausedFalse() {
        helper.createChannel("pr-resume-1", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.pauseChannel("pr-resume-1", null);
        ChannelDetail detail = helper.resumeChannel("pr-resume-1", null);
        assertFalse(detail.paused(), "channel should not be paused after resume_channel");
    }

    @Test
    @TestTransaction
    void resumeUnpausedChannelIsIdempotent() {
        helper.createChannel("pr-resume-2", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        // Resume a channel that was never paused — must not throw
        ChannelDetail detail = helper.resumeChannel("pr-resume-2", null);
        assertFalse(detail.paused(), "channel should not be paused after resuming an already-active channel");
    }

    @Test
    @TestTransaction
    void resumeUnknownChannelThrowsIllegalArgument() {
        assertThrows(IllegalArgumentException.class, () -> helper.resumeChannel("no-such-channel", null));
    }

    // -------------------------------------------------------------------------
    // Unit — send_message blocked on paused channel
    // -------------------------------------------------------------------------

    @Test
    @TestTransaction
    void sendMessageOnPausedChannelThrows() {
        helper.createChannel("pr-send-1", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.pauseChannel("pr-send-1", null);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> helper.sendMessage("pr-send-1", "alice", "status", "hello", null, null, null, null, null, null, null, null, null));
        assertTrue(ex.getMessage().toLowerCase().contains("paused"),
                "error message should mention 'paused'");
    }

    @Test
    @TestTransaction
    void sendMessageOnResumedChannelSucceeds() {
        helper.createChannel("pr-send-2", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.pauseChannel("pr-send-2", null);
        helper.resumeChannel("pr-send-2", null);

        assertDoesNotThrow(
                () -> helper.sendMessage("pr-send-2", "alice", "status", "hello", null, null, null, null, null, null, null, null, null));
    }

    // -------------------------------------------------------------------------
    // Unit — check_messages on paused channel
    // -------------------------------------------------------------------------

    @Test
    @TestTransaction
    void checkMessagesOnPausedChannelReturnsEmptyWithStatus() {
        helper.createChannel("pr-check-1", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("pr-check-1", "alice", "status", "before pause", null, null, null, null, null, null, null, null, null);
        helper.pauseChannel("pr-check-1", null);

        CheckResult result = helper.checkMessages("pr-check-1", 0L, 10, null, null, null);
        assertTrue(result.isEmpty(),
                "check_messages on paused channel should return no messages");
        assertNotNull(result.barrierStatus(), "paused channel should return a non-null status");
        assertTrue(result.barrierStatus().toLowerCase().contains("paused"),
                "status should indicate channel is paused");
    }

    @Test
    @TestTransaction
    void checkMessagesOnResumedChannelReturnsMessages() {
        helper.createChannel("pr-check-2", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("pr-check-2", "alice", "status", "before pause", null, null, null, null, null, null, null, null, null);
        helper.pauseChannel("pr-check-2", null);
        helper.resumeChannel("pr-check-2", null);

        CheckResult result = helper.checkMessages("pr-check-2", 0L, 10, null, null, null);
        assertEquals(1, result.size(),
                "check_messages on resumed channel should return messages again");
        assertNull(result.barrierStatus(), "resumed channel should have null barrierStatus");
    }

    // -------------------------------------------------------------------------
    // Unit — ChannelDetail reflects paused state
    // -------------------------------------------------------------------------

    @Test
    @TestTransaction
    void channelDetailPausedFieldReflectsCurrentState() {
        helper.createChannel("pr-detail-1", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        // Initially not paused
        ChannelDetail before = helper.listChannels().stream()
                .filter(d -> d.name().equals("pr-detail-1"))
                .findFirst().orElseThrow();
        assertFalse(before.paused());

        helper.pauseChannel("pr-detail-1", null);

        ChannelDetail after = helper.listChannels().stream()
                .filter(d -> d.name().equals("pr-detail-1"))
                .findFirst().orElseThrow();
        assertTrue(after.paused());
    }

    // -------------------------------------------------------------------------
    // Integration — full pause → fail → resume → succeed cycle
    // -------------------------------------------------------------------------

    @Test
    @TestTransaction
    void fullPauseResumeCycle() {
        helper.createChannel("pr-cycle-1", "Test", "APPEND", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        // 1. Send before pause — succeeds
        assertDoesNotThrow(() -> helper.sendMessage("pr-cycle-1", "alice", "status", "msg1", null, null, null, null, null, null, null, null, null));

        // 2. Pause
        helper.pauseChannel("pr-cycle-1", null);

        // 3. Send while paused — fails
        assertThrows(IllegalStateException.class, () -> helper.sendMessage("pr-cycle-1", "alice", "status", "msg2", null, null, null, null, null, null, null, null, null));

        // 4. check_messages while paused — empty + status
        CheckResult paused = helper.checkMessages("pr-cycle-1", 0L, 10, null, null, null);
        assertTrue(paused.isEmpty());
        assertNotNull(paused.barrierStatus());

        // 5. Resume
        helper.resumeChannel("pr-cycle-1", null);

        // 6. Send after resume — succeeds
        assertDoesNotThrow(() -> helper.sendMessage("pr-cycle-1", "alice", "status", "msg3", null, null, null, null, null, null, null, null, null));

        // 7. check_messages after resume — shows msg1 and msg3 (msg2 never stored)
        CheckResult resumed = helper.checkMessages("pr-cycle-1", 0L, 10, null, null, null);
        assertEquals(2, resumed.size());
        assertNull(resumed.barrierStatus());
    }

    // -------------------------------------------------------------------------
    // E2E — multiple agents; pause blocks all; resume unblocks all
    // -------------------------------------------------------------------------

    @Test
    @TestTransaction
    void e2eMultipleAgentsBlockedByPause() {
        helper.createChannel("pr-e2e-1", "Test", "APPEND", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.register("alice-agent", "Alice", java.util.List.of(), null, null);
        helper.register("bob-agent", "Bob", java.util.List.of(), null, null);

        // Both can send before pause
        helper.sendMessage("pr-e2e-1", "alice-agent", "command", "alice work", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("pr-e2e-1", "bob-agent", "status", "bob work", null, null, null, null, null, null, null, null, null);

        // Human pauses the channel
        helper.pauseChannel("pr-e2e-1", null);

        // Neither agent can send
        assertThrows(IllegalStateException.class, () -> helper.sendMessage("pr-e2e-1", "alice-agent", "status", "update", null, null, null, null, null, null, null, null, null));
        assertThrows(IllegalStateException.class, () -> helper.sendMessage("pr-e2e-1", "bob-agent", "status", "update", null, null, null, null, null, null, null, null, null));

        // Human resumes
        helper.resumeChannel("pr-e2e-1", null);

        // Both agents can send again
        assertDoesNotThrow(() -> helper.sendMessage("pr-e2e-1", "alice-agent", "status", "alice resumes", null, null, null, null, null, null, null, null, null));
        assertDoesNotThrow(() -> helper.sendMessage("pr-e2e-1", "bob-agent", "status", "bob resumes", null, null, null, null, null, null, null, null, null));

        // All 4 messages visible (2 before pause, 2 after resume)
        CheckResult result = helper.checkMessages("pr-e2e-1", 0L, 20, null, null, null);
        assertEquals(4, result.size());
    }
}
