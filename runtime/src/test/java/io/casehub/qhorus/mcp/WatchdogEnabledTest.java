package io.casehub.qhorus.mcp;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.testing.QhorusTestHelper;
import io.casehub.qhorus.testing.QhorusTestHelper.CheckResult;
import io.casehub.qhorus.testing.QhorusTestHelper.WatchdogSummary;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.runtime.message.CommitmentService;
import io.casehub.qhorus.runtime.watchdog.WatchdogEvaluationService;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

/**
 * Issue #44 — Watchdog CRUD and condition evaluation (enabled profile).
 *
 * <p>
 * Evaluation logic is tested by calling {@code WatchdogEvaluationService.evaluateAll()}
 * directly rather than waiting for the Quarkus Scheduler to fire.
 *
 * <p>
 * Refs #44, Epic #36.
 */
@QuarkusTest
@TestProfile(io.casehub.qhorus.api.WatchdogEnabledProfile.class)
class WatchdogEnabledTest {

    @Inject QhorusTestHelper helper;

    @Inject
    WatchdogEvaluationService watchdogService;

    @Inject
    CommitmentService commitmentService;

    // -------------------------------------------------------------------------
    // CRUD — register / list / delete
    // -------------------------------------------------------------------------

    @Test
    @TestTransaction
    void registerWatchdogCreatesEntry() {
        helper.createChannel("wd-notif-1", "Alerts", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        WatchdogSummary summary = helper.registerWatchdog(
                "BARRIER_STUCK", "any-channel", 300, null, null, "wd-notif-1", "admin", null);

        assertNotNull(summary.id());
        assertEquals("BARRIER_STUCK", summary.conditionType());
        assertEquals("any-channel", summary.targetName());
        assertEquals(300, summary.thresholdSeconds());
        assertEquals("wd-notif-1", summary.notificationChannel());
    }

    @Test
    @TestTransaction
    void listWatchdogsShowsRegisteredEntry() {
        helper.createChannel("wd-notif-2", "Alerts", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        WatchdogSummary created = helper.registerWatchdog(
                "APPROVAL_PENDING", "*", 60, null, null, "wd-notif-2", "admin", null);

        var list = helper.listWatchdogs();
        assertTrue(list.stream().anyMatch(w -> created.id().equals(w.id())));
    }

    @Test
    @TestTransaction
    void deleteWatchdogRemovesIt() {
        helper.createChannel("wd-notif-3", "Alerts", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        WatchdogSummary created = helper.registerWatchdog(
                "CHANNEL_IDLE", "work-channel", 600, null, null, "wd-notif-3", "admin", null);

        helper.deleteWatchdog(created.id());

        var list = helper.listWatchdogs();
        assertFalse(list.stream().anyMatch(w -> created.id().equals(w.id())));
    }

    @Test
    @TestTransaction
    void deleteUnknownWatchdogReturnsFalse() {
        QhorusTestHelper.DeleteWatchdogResult result = helper.deleteWatchdog(UUID.randomUUID().toString());
        assertFalse(result.deleted());
    }

    // -------------------------------------------------------------------------
    // Condition evaluation — BARRIER_STUCK
    // -------------------------------------------------------------------------

    @Test
    @TestTransaction
    void barrierStuckWatchdogFiresAlert() {
        helper.createChannel("wd-barrier-1", "Test", "BARRIER", "alice,bob", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.createChannel("wd-notif-b1", "Alerts", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        // Register watchdog with threshold of 0s (always fires if not released)
        helper.registerWatchdog("BARRIER_STUCK", "wd-barrier-1", 0, null, null,
                "wd-notif-b1", "admin", null);

        // Alice writes but barrier is still stuck (bob missing)
        helper.sendMessage("wd-barrier-1", "alice", "status", "alice done", null, null, null, null, null, null, null, null, null);

        // Evaluate directly
        watchdogService.evaluateAll();

        // Alert should appear in notification channel
        var alerts = helper.checkMessages("wd-notif-b1", 0L, 10, null, null, null);
        assertFalse(alerts.isEmpty(),
                "BARRIER_STUCK watchdog should post an alert to notification channel");
        assertTrue(alerts.get(0).content().contains("BARRIER_STUCK"));
    }

    // -------------------------------------------------------------------------
    // Condition evaluation — APPROVAL_PENDING
    // -------------------------------------------------------------------------

    @Test
    @TestTransaction
    void approvalPendingWatchdogFiresAlert() {
        helper.createChannel("wd-approval-1", "Approvals", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.createChannel("wd-notif-a1", "Alerts", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        String corrId = UUID.randomUUID().toString();

        // Register watchdog with threshold of 0s
        helper.registerWatchdog("APPROVAL_PENDING", "*", 0, null, null, "wd-notif-a1", "admin", null);

        // Register a commitment directly — simulates what wait_for_reply does
        var ch = helper.listChannels().stream()
                .filter(c -> "wd-approval-1".equals(c.name()))
                .findFirst().orElseThrow();
        commitmentService.open(UUID.randomUUID(), corrId, ch.channelId(),
                MessageType.QUERY, "test-requester", null, Instant.now().plusSeconds(60));

        // Evaluate
        watchdogService.evaluateAll();

        var alerts = helper.checkMessages("wd-notif-a1", 0L, 10, null, null, null);
        assertFalse(alerts.isEmpty(),
                "APPROVAL_PENDING watchdog should fire");
        assertTrue(alerts.get(0).content().contains("APPROVAL_PENDING"));
    }

    // -------------------------------------------------------------------------
    // Condition evaluation — CHANNEL_IDLE
    // -------------------------------------------------------------------------

    @Test
    @TestTransaction
    void channelIdleWatchdogFiresAlert() {
        helper.createChannel("wd-idle-1", "Idle Channel", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.createChannel("wd-notif-i1", "Alerts", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        // Register watchdog with threshold 0s (always fires for any channel)
        helper.registerWatchdog("CHANNEL_IDLE", "wd-idle-1", 0, null, null,
                "wd-notif-i1", "admin", null);

        watchdogService.evaluateAll();

        var alerts = helper.checkMessages("wd-notif-i1", 0L, 10, null, null, null);
        assertFalse(alerts.isEmpty(), "CHANNEL_IDLE watchdog should fire");
        assertTrue(alerts.get(0).content().contains("CHANNEL_IDLE"));
    }

    // -------------------------------------------------------------------------
    // Condition evaluation — QUEUE_DEPTH
    // -------------------------------------------------------------------------

    @Test
    @TestTransaction
    void queueDepthWatchdogFiresWhenThresholdExceeded() {
        helper.createChannel("wd-queue-1", "Work Queue", "COLLECT", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.createChannel("wd-notif-q1", "Alerts", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        helper.registerWatchdog("QUEUE_DEPTH", "wd-queue-1", null, 2, null,
                "wd-notif-q1", "admin", null);

        // Add 3 messages — exceeds threshold of 2
        helper.sendMessage("wd-queue-1", "a1", "status", "m1", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("wd-queue-1", "a2", "status", "m2", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("wd-queue-1", "a3", "status", "m3", null, null, null, null, null, null, null, null, null);

        watchdogService.evaluateAll();

        var alerts = helper.checkMessages("wd-notif-q1", 0L, 10, null, null, null);
        assertFalse(alerts.isEmpty(), "QUEUE_DEPTH watchdog should fire");
        assertTrue(alerts.get(0).content().contains("QUEUE_DEPTH"));
    }

    @Test
    @TestTransaction
    void queueDepthWatchdogDoesNotFireBelowThreshold() {
        helper.createChannel("wd-queue-2", "Work Queue", "COLLECT", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.createChannel("wd-notif-q2", "Alerts", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        helper.registerWatchdog("QUEUE_DEPTH", "wd-queue-2", null, 5, null,
                "wd-notif-q2", "admin", null);

        // Only 2 messages — below threshold of 5
        helper.sendMessage("wd-queue-2", "a1", "status", "m1", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("wd-queue-2", "a2", "status", "m2", null, null, null, null, null, null, null, null, null);

        watchdogService.evaluateAll();

        var alerts = helper.checkMessages("wd-notif-q2", 0L, 10, null, null, null);
        assertTrue(alerts.isEmpty(), "QUEUE_DEPTH watchdog should not fire below threshold");
    }

    // -------------------------------------------------------------------------
    // Debounce — doesn't re-fire within threshold window
    // -------------------------------------------------------------------------

    @Test
    @TestTransaction
    void watchdogDoesNotRefireWithinDebounceWindow() {
        helper.createChannel("wd-debounce-1", "Idle", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.createChannel("wd-notif-d1", "Alerts", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        // Threshold 0s — fires immediately
        helper.registerWatchdog("CHANNEL_IDLE", "wd-debounce-1", 0, null, null,
                "wd-notif-d1", "admin", null);

        // First evaluation — fires
        watchdogService.evaluateAll();

        // Second evaluation — debounce should prevent re-fire
        // (lastFiredAt is now set, and threshold is 0s — allow 1s buffer)
        watchdogService.evaluateAll();

        var alerts = helper.checkMessages("wd-notif-d1", 0L, 20, null, null, null);
        assertEquals(1, alerts.size(),
                "watchdog should fire exactly once due to debounce");
    }

    // -------------------------------------------------------------------------
    // E2E — register watchdog → condition met → alert posted
    // -------------------------------------------------------------------------

    @Test
    @TestTransaction
    void e2eBarrierWatchdogFullLifecycle() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String barrierChannel = "wd-e2e-barrier-" + suffix;
        String alertsChannel = "wd-e2e-alerts-" + suffix;

        helper.createChannel(barrierChannel, "Work", "BARRIER", "alice,bob", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.createChannel(alertsChannel, "Alerts", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        // 1. Ops team registers a watchdog
        WatchdogSummary watchdog = helper.registerWatchdog(
                "BARRIER_STUCK", barrierChannel, 0, null, null,
                alertsChannel, "ops-team", null);
        assertNotNull(watchdog.id());

        // 2. Alice writes but bob doesn't — barrier is stuck
        helper.sendMessage(barrierChannel, "alice", "status", "done", null, null, null, null, null, null, null, null, null);

        // 3. Watchdog fires
        watchdogService.evaluateAll();

        // 4. Alert visible in notification channel
        var alerts = helper.checkMessages(alertsChannel, 0L, 10, null, null, null);
        assertEquals(1, alerts.size());
        assertEquals("system:watchdog", alerts.get(0).sender());

        // 5. Ops deletes watchdog after acknowledging
        QhorusTestHelper.DeleteWatchdogResult deleted = helper.deleteWatchdog(watchdog.id());
        assertTrue(deleted.deleted());
        assertTrue(helper.listWatchdogs().stream().noneMatch(w -> watchdog.id().equals(w.id())));
    }
}
