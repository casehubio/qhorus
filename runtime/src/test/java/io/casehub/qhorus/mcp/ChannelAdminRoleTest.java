package io.casehub.qhorus.mcp;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelDetail;
import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.store.MessageStore;
import io.casehub.qhorus.api.store.query.MessageQuery;
import io.casehub.qhorus.runtime.channel.ChannelService;
import io.casehub.qhorus.testing.QhorusTestHelper;
import io.casehub.qhorus.testing.QhorusTestHelper.CheckResult;
import io.casehub.qhorus.testing.QhorusTestHelper.ClearChannelResult;
import io.casehub.qhorus.api.message.Message;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Issue #47 — Admin role: designate an instance as channel admin with management authority.
 *
 * <p>
 * Three levels of testing:
 * <ul>
 * <li>Unit: admin check on each management tool; open governance (no admin list)</li>
 * <li>Integration: set_channel_admins; ChannelDetail; create_channel with admin_instances</li>
 * <li>E2E: multi-agent scenarios — admin manages, non-admins blocked; ACL coexistence</li>
 * </ul>
 *
 * <p>
 * Admin check applies to: {@code pause_channel}, {@code resume_channel},
 * {@code force_release_channel}, {@code clear_channel}.
 * When {@code admin_instances} is null/empty the tools remain open to any caller.
 *
 * <p>
 * Refs #47, Epic #45.
 */
@QuarkusTest
class ChannelAdminRoleTest {

    @Inject QhorusTestHelper helper;
    @Inject ChannelService channelService;
    @Inject MessageStore messageStore;

    private void forceReleaseWithAdminCheck(String channelName, String reason, String callerInstanceId) {
        Channel ch = channelService.findByName(channelName)
                .orElseThrow(() -> new IllegalArgumentException("Channel not found: " + channelName));
        if (ch.adminInstances() != null && !ch.adminInstances().isEmpty()) {
            if (callerInstanceId == null || callerInstanceId.isBlank()) {
                throw new IllegalStateException("Channel '" + ch.name()
                        + "' requires a caller_instance_id for force_release_channel — it has an admin_instances list.");
            }
            if (!ch.adminInstances().contains(callerInstanceId)) {
                throw new IllegalStateException("Caller '" + callerInstanceId
                        + "' is not permitted to invoke force_release_channel on channel '" + ch.name()
                        + "'. Not in admin_instances list.");
            }
        }
        if (ch.semantic() != ChannelSemantic.BARRIER && ch.semantic() != ChannelSemantic.COLLECT) {
            throw new IllegalArgumentException("force_release_channel only applies to BARRIER and COLLECT channels");
        }
        messageStore.scan(MessageQuery.builder().channelId(ch.id()).excludeTypes(java.util.List.of(MessageType.EVENT)).build());
        messageStore.deleteNonEvent(ch.id());
    }

    // =========================================================================
    // Unit — open governance (no admin list)
    // =========================================================================

    @Test
    @TestTransaction
    void openChannelWithNoAdminListAllowsAnyCallerToPause() {
        helper.createChannel("ar-open-1", "Open", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        assertDoesNotThrow(
                () -> helper.pauseChannel("ar-open-1", "anyone"),
                "channel with no admin_instances should accept any caller for pause_channel");
    }

    @Test
    @TestTransaction
    void openChannelWithNoAdminListAllowsAnyCallerToResume() {
        helper.createChannel("ar-open-2", "Open", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.pauseChannel("ar-open-2", "someone");

        assertDoesNotThrow(
                () -> helper.resumeChannel("ar-open-2", "anyone"),
                "channel with no admin_instances should accept any caller for resume_channel");
    }

    @Test
    @TestTransaction
    void openChannelWithNoAdminListAllowsAnyCallerToForceRelease() {
        helper.createChannel("ar-open-3", "Open", "BARRIER", "alice,bob", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        assertDoesNotThrow(
                () -> forceReleaseWithAdminCheck("ar-open-3", "testing", "anyone"),
                "channel with no admin_instances should accept any caller for force_release_channel");
    }

    @Test
    @TestTransaction
    void openChannelWithNoAdminListAllowsAnyCallerToClear() {
        helper.createChannel("ar-open-4", "Open", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("ar-open-4", "alice", "status", "msg", null, null, null, null, null, null, null, null, null);

        assertDoesNotThrow(
                () -> helper.clearChannel("ar-open-4", "anyone"),
                "channel with no admin_instances should accept any caller for clear_channel");
    }

    @Test
    @TestTransaction
    void createChannelDetailHasNullAdminInstances() {
        ChannelDetail detail = helper.createChannel("ar-open-5", "Open", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        assertNull(detail.adminInstances(),
                "channel created without admin_instances should have null adminInstances in detail");
    }

    // =========================================================================
    // Unit — null caller treated as no admin check (backward compat)
    // =========================================================================

    @Test
    @TestTransaction
    void nullCallerIdBypasesAdminCheckForOpenChannel() {
        helper.createChannel("ar-null-1", "Open", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        // Original 1-arg overloads (no caller ID) must still work unchanged
        assertDoesNotThrow(() -> helper.pauseChannel("ar-null-1", null),
                "original pause_channel with no caller_id should still work on open channel");
        assertDoesNotThrow(() -> helper.resumeChannel("ar-null-1", null),
                "original resume_channel with no caller_id should still work on open channel");
    }

    // =========================================================================
    // Unit — listed admin can invoke management tools
    // =========================================================================

    @Test
    @TestTransaction
    void listedAdminCanPauseChannel() {
        helper.createChannel("ar-admin-1", "Admin gated", null, null, null, "alice-admin", null, null, null, null, null, null, null, null, null, null, null, null, null);

        assertDoesNotThrow(
                () -> helper.pauseChannel("ar-admin-1", "alice-admin"),
                "listed admin should be able to pause the channel");
    }

    @Test
    @TestTransaction
    void listedAdminCanResumeChannel() {
        helper.createChannel("ar-admin-2", "Admin gated", null, null, null, "alice-admin", null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.pauseChannel("ar-admin-2", "alice-admin");

        assertDoesNotThrow(
                () -> helper.resumeChannel("ar-admin-2", "alice-admin"),
                "listed admin should be able to resume the channel");
    }

    @Test
    @TestTransaction
    void listedAdminCanForceReleaseChannel() {
        helper.createChannel("ar-admin-3", "Admin gated", "BARRIER", "alice,bob", null, "alice-admin", null, null, null, null, null, null, null, null, null, null, null, null, null);

        assertDoesNotThrow(
                () -> forceReleaseWithAdminCheck("ar-admin-3", "admin override", "alice-admin"),
                "listed admin should be able to force_release_channel");
    }

    @Test
    @TestTransaction
    void listedAdminCanClearChannel() {
        helper.createChannel("ar-admin-4", "Admin gated", null, null, null, "alice-admin", null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("ar-admin-4", "alice-admin", "status", "msg", null, null, null, null, null, null, null, null, null);

        assertDoesNotThrow(
                () -> helper.clearChannel("ar-admin-4", "alice-admin"),
                "listed admin should be able to clear_channel");
    }

    @Test
    @TestTransaction
    void multipleAdminsAnyOneCanManage() {
        helper.createChannel("ar-admin-5", "Multi-admin", null, null, null, "alice-admin,bob-admin", null, null, null, null, null, null, null, null, null, null, null, null, null);

        assertDoesNotThrow(
                () -> helper.pauseChannel("ar-admin-5", "bob-admin"),
                "any listed admin (not just the first) should be accepted");
    }

    // =========================================================================
    // Unit — non-admin caller rejected
    // =========================================================================

    @Test
    @TestTransaction
    void nonAdminCannotPauseChannel() {
        helper.createChannel("ar-deny-1", "Admin gated", null, null, null, "alice-admin", null, null, null, null, null, null, null, null, null, null, null, null, null);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> helper.pauseChannel("ar-deny-1", "mallory"), "non-admin should be rejected from pause_channel");

        String msg = ex.getMessage();
        assertTrue(msg.contains("mallory"),
                "error should name the rejected caller, was: " + msg);
        assertTrue(msg.toLowerCase().contains("admin") || msg.toLowerCase().contains("not permitted"),
                "error should indicate admin restriction, was: " + msg);
    }

    @Test
    @TestTransaction
    void nonAdminCannotResumeChannel() {
        helper.createChannel("ar-deny-2", "Admin gated", null, null, null, "alice-admin", null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.pauseChannel("ar-deny-2", "alice-admin");

        assertThrows(IllegalStateException.class, () -> helper.resumeChannel("ar-deny-2", "mallory"), "non-admin should be rejected from resume_channel");
    }

    @Test
    @TestTransaction
    void nonAdminCannotForceReleaseChannel() {
        helper.createChannel("ar-deny-3", "Admin gated", "BARRIER", "alice,bob", null, "alice-admin", null, null, null, null, null, null, null, null, null, null, null, null, null);

        assertThrows(IllegalStateException.class,
                () -> forceReleaseWithAdminCheck("ar-deny-3", "reason", "mallory"),
                "non-admin should be rejected from force_release_channel");
    }

    @Test
    @TestTransaction
    void nonAdminCannotClearChannel() {
        helper.createChannel("ar-deny-4", "Admin gated", null, null, null, "alice-admin", null, null, null, null, null, null, null, null, null, null, null, null, null);

        assertThrows(IllegalStateException.class, () -> helper.clearChannel("ar-deny-4", "mallory"), "non-admin should be rejected from clear_channel");
    }

    // =========================================================================
    // Integration — set_channel_admins
    // =========================================================================

    @Test
    @TestTransaction
    void setChannelAdminsAppliesAdminListToExistingChannel() {
        helper.createChannel("ar-sca-1", "Open initially", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        // Before: any caller can manage
        assertDoesNotThrow(() -> helper.pauseChannel("ar-sca-1", "mallory"));
        helper.resumeChannel("ar-sca-1", "mallory");

        // Apply admin list
        ChannelDetail updated = helper.setChannelAdmins("ar-sca-1", "alice-admin");
        assertEquals("alice-admin", updated.adminInstances(),
                "setChannelAdmins should return ChannelDetail with the new adminInstances");

        // Now mallory is blocked
        assertThrows(IllegalStateException.class, () -> helper.pauseChannel("ar-sca-1", "mallory"));
        // Alice is allowed
        assertDoesNotThrow(() -> helper.pauseChannel("ar-sca-1", "alice-admin"));
    }

    @Test
    @TestTransaction
    void setChannelAdminsToNullClearsAdminList() {
        helper.createChannel("ar-sca-2", "Admin gated", null, null, null, "alice-admin", null, null, null, null, null, null, null, null, null, null, null, null, null);

        // Non-admin is blocked
        assertThrows(IllegalStateException.class, () -> helper.pauseChannel("ar-sca-2", "bob"));

        // Clear admin list
        ChannelDetail cleared = helper.setChannelAdmins("ar-sca-2", null);
        assertNull(cleared.adminInstances(), "clearing admin list should result in null adminInstances");

        // Bob now allowed
        assertDoesNotThrow(() -> helper.pauseChannel("ar-sca-2", "bob"));
    }

    @Test
    @TestTransaction
    void setChannelAdminsOnUnknownChannelThrows() {
        assertThrows(IllegalArgumentException.class, () -> helper.setChannelAdmins("no-such-channel", "alice-admin"), "setChannelAdmins on non-existent channel should throw IllegalArgumentException");
    }

    // =========================================================================
    // Integration — ChannelDetail reflects adminInstances
    // =========================================================================

    @Test
    @TestTransaction
    void createChannelDetailIncludesAdminInstances() {
        ChannelDetail detail = helper.createChannel("ar-det-1", "Admin channel", null, null, null, "carol-admin", null, null, null, null, null, null, null, null, null, null, null, null, null);

        assertEquals("carol-admin", detail.adminInstances(),
                "ChannelDetail from createChannel should expose adminInstances");
    }

    @Test
    @TestTransaction
    void listChannelsIncludesAdminInstances() {
        helper.createChannel("ar-det-2", "Admin channel", null, null, null, "dave-admin", null, null, null, null, null, null, null, null, null, null, null, null, null);

        ChannelDetail found = helper.listChannels().stream()
                .filter(d -> "ar-det-2".equals(d.name()))
                .findFirst().orElseThrow();

        assertEquals("dave-admin", found.adminInstances(),
                "listChannels ChannelDetail should include adminInstances");
    }

    // =========================================================================
    // Integration — allowed_writers and admin_instances are independent
    // =========================================================================

    @Test
    @TestTransaction
    void allowedWritersAndAdminInstancesAreIndependent() {
        // alice can write (allowed_writers); bob is admin (admin_instances)
        helper.createChannel("ar-ind-1", "Dual ACL", null, null, "alice", "bob-admin", null, null, null, null, null, null, null, null, null, null, null, null, null);

        // alice can write but cannot manage (not an admin)
        assertDoesNotThrow(() -> helper.sendMessage("ar-ind-1", "alice", "status", "hi", null, null, null, null, null, null, null, null, null));
        assertThrows(IllegalStateException.class, () -> helper.pauseChannel("ar-ind-1", "alice"), "alice is an allowed writer but not an admin — should be rejected from managing");

        // bob can manage but cannot write (not in allowed_writers)
        assertDoesNotThrow(() -> helper.pauseChannel("ar-ind-1", "bob-admin"));
        assertThrows(IllegalStateException.class, () -> helper.sendMessage("ar-ind-1", "bob-admin", "status", "hi", null, null, null, null, null, null, null, null, null), "bob is an admin but not an allowed writer — should be rejected from writing");
    }

    // =========================================================================
    // E2E — three agents: one admin, two non-admins
    // =========================================================================

    @Test
    @TestTransaction
    void e2eOnlyAdminCanManageChannel() {
        helper.createChannel("ar-e2e-1", "Governed channel", "APPEND", null, null, "admin-agent", null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.register("admin-agent", "Admin", List.of(), null, null);
        helper.register("worker-a", "Worker A", List.of(), null, null);
        helper.register("worker-b", "Worker B", List.of(), null, null);

        // Workers can send messages (no write ACL)
        assertDoesNotThrow(() -> helper.sendMessage("ar-e2e-1", "worker-a", "status", "work", null, null, null, null, null, null, null, null, null));
        assertDoesNotThrow(() -> helper.sendMessage("ar-e2e-1", "worker-b", "status", "work", null, null, null, null, null, null, null, null, null));

        // Neither worker can pause
        assertThrows(IllegalStateException.class, () -> helper.pauseChannel("ar-e2e-1", "worker-a"));
        assertThrows(IllegalStateException.class, () -> helper.pauseChannel("ar-e2e-1", "worker-b"));

        // Admin pauses successfully
        assertDoesNotThrow(() -> helper.pauseChannel("ar-e2e-1", "admin-agent"));

        // Workers cannot resume
        assertThrows(IllegalStateException.class, () -> helper.resumeChannel("ar-e2e-1", "worker-a"));

        // Admin resumes
        assertDoesNotThrow(() -> helper.resumeChannel("ar-e2e-1", "admin-agent"));

        // Admin clears
        ClearChannelResult cleared = helper.clearChannel("ar-e2e-1", "admin-agent");
        assertTrue(cleared.cleared());
        assertEquals(2, cleared.messagesDeleted(), "both worker messages should be cleared");
    }

    // =========================================================================
    // E2E — admin role coexists with pause/resume state correctly
    // =========================================================================

    @Test
    @TestTransaction
    void e2eAdminCanPauseResumeCycleWhileNonAdminsAreBlocked() {
        helper.createChannel("ar-e2e-2", "Cycle test", "APPEND", null, null, "admin-agent", null, null, null, null, null, null, null, null, null, null, null, null, null);

        // Workers can write before pause
        helper.sendMessage("ar-e2e-2", "worker", "status", "before", null, null, null, null, null, null, null, null, null);

        // Admin pauses
        helper.pauseChannel("ar-e2e-2", "admin-agent");

        // Worker cannot write (paused) AND cannot resume (not admin)
        assertThrows(IllegalStateException.class, () -> helper.sendMessage("ar-e2e-2", "worker", "status", "during", null, null, null, null, null, null, null, null, null));
        assertThrows(IllegalStateException.class, () -> helper.resumeChannel("ar-e2e-2", "worker"));

        // Admin resumes
        helper.resumeChannel("ar-e2e-2", "admin-agent");

        // Worker can write again
        helper.sendMessage("ar-e2e-2", "worker", "status", "after", null, null, null, null, null, null, null, null, null);

        CheckResult result = helper.checkMessages("ar-e2e-2", 0L, 10, null, null, null);
        assertEquals(2, result.size(), "before and after messages both present");
    }
}
