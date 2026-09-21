package io.casehub.qhorus.mcp;

import static org.junit.jupiter.api.Assertions.*;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehub.qhorus.api.channel.ChannelDetail;
import io.casehub.qhorus.testing.QhorusTestHelper;
import java.util.List;
import io.casehub.qhorus.api.message.Message;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Issue #48 — Rate limiting: per-channel and per-instance message throttling.
 *
 * <p>
 * Three levels of testing:
 * <ul>
 * <li>Unit: per-channel limit enforcement; per-instance limit enforcement; no-limit pass-through</li>
 * <li>Integration: set_channel_rate_limits; ChannelDetail; limits coexist independently</li>
 * <li>E2E: full multi-sender scenarios; per-instance isolation; rejection count stability</li>
 * </ul>
 *
 * <p>
 * Rate windows are in-memory sliding windows (60 seconds). Limits reset on restart (documented).
 * Cleanup happens on the request path — no background scheduler.
 *
 * <p>
 * Refs #48, Epic #45.
 */
@QuarkusTest
class ChannelRateLimitTest {

    @Inject QhorusTestHelper helper;

    // =========================================================================
    // Unit — no limits (unrestricted)
    // =========================================================================

    @Test
    @TestTransaction
    void channelWithNoLimitsAllowsUnlimitedMessages() {
        helper.createChannel("rl-open-1", "Unlimited", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        for (int i = 0; i < 10; i++) {
            final int n = i;
            assertDoesNotThrow(
                    () -> helper.sendMessage("rl-open-1", "sender", "status", "msg" + n, null, null, null, null, null, null, null, null, null),
                    "channel with no rate limits should accept unlimited messages");
        }
    }

    @Test
    @TestTransaction
    void createChannelDetailHasNullRateLimits() {
        ChannelDetail detail = helper.createChannel("rl-open-2", "No limits", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        assertNull(detail.rateLimitPerChannel(), "no rateLimitPerChannel should be null");
        assertNull(detail.rateLimitPerInstance(), "no rateLimitPerInstance should be null");
    }

    // =========================================================================
    // Unit — per-channel limit
    // =========================================================================

    @Test
    @TestTransaction
    void messagesUnderPerChannelLimitPass() {
        helper.createChannel("rl-ch-1", "Per-channel limit", null, null, null, null, 3, null, null, null, null, null, null, null, null, null, null, null, null);

        // First 3 messages pass
        assertDoesNotThrow(() -> helper.sendMessage("rl-ch-1", "alice", "status", "1", null, null, null, null, null, null, null, null, null));
        assertDoesNotThrow(() -> helper.sendMessage("rl-ch-1", "alice", "status", "2", null, null, null, null, null, null, null, null, null));
        assertDoesNotThrow(() -> helper.sendMessage("rl-ch-1", "alice", "status", "3", null, null, null, null, null, null, null, null, null));
    }

    @Test
    @TestTransaction
    void messageAtPerChannelLimitPasses() {
        helper.createChannel("rl-ch-2", "Per-channel limit", null, null, null, null, 2, null, null, null, null, null, null, null, null, null, null, null, null);

        assertDoesNotThrow(() -> helper.sendMessage("rl-ch-2", "alice", "status", "1", null, null, null, null, null, null, null, null, null));
        assertDoesNotThrow(() -> helper.sendMessage("rl-ch-2", "alice", "status", "2", null, null, null, null, null, null, null, null, null),
                "message exactly at the limit (not over) should be accepted");
    }

    @Test
    @TestTransaction
    void messageOverPerChannelLimitIsRejectedWithClearError() {
        helper.createChannel("rl-ch-3", "Per-channel limit", null, null, null, null, 2, null, null, null, null, null, null, null, null, null, null, null, null);

        helper.sendMessage("rl-ch-3", "alice", "status", "1", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("rl-ch-3", "alice", "status", "2", null, null, null, null, null, null, null, null, null);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> helper.sendMessage("rl-ch-3", "alice", "status", "3", null, null, null, null, null, null, null, null, null), "message over per-channel limit should be rejected");

        String msg = ex.getMessage().toLowerCase();
        assertTrue(msg.contains("rate") || msg.contains("limit"),
                "error should mention rate limiting, was: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("rl-ch-3"),
                "error should name the channel, was: " + ex.getMessage());
    }

    @Test
    @TestTransaction
    void perChannelLimitCountsAcrossAllSenders() {
        helper.createChannel("rl-ch-4", "Per-channel limit", null, null, null, null, 2, null, null, null, null, null, null, null, null, null, null, null, null);

        // Two different senders each send one message — together they hit the limit
        helper.sendMessage("rl-ch-4", "alice", "status", "from alice", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("rl-ch-4", "bob", "status", "from bob", null, null, null, null, null, null, null, null, null);

        // Third message from anyone is rejected
        assertThrows(IllegalStateException.class, () -> helper.sendMessage("rl-ch-4", "carol", "status", "from carol", null, null, null, null, null, null, null, null, null), "per-channel limit counts across all senders");
    }

    @Test
    @TestTransaction
    void rejectedMessageDoesNotIncrementCount() {
        helper.createChannel("rl-ch-5", "Per-channel limit", null, null, null, null, 2, null, null, null, null, null, null, null, null, null, null, null, null);

        helper.sendMessage("rl-ch-5", "alice", "status", "1", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("rl-ch-5", "alice", "status", "2", null, null, null, null, null, null, null, null, null);

        // Third is rejected
        assertThrows(IllegalStateException.class, () -> helper.sendMessage("rl-ch-5", "alice", "status", "3", null, null, null, null, null, null, null, null, null));

        // Fourth is also rejected — count should not have incremented on the rejected message
        assertThrows(IllegalStateException.class, () -> helper.sendMessage("rl-ch-5", "alice", "status", "4", null, null, null, null, null, null, null, null, null), "rejected messages should not increment the rate limit counter");
    }

    // =========================================================================
    // Unit — per-instance limit
    // =========================================================================

    @Test
    @TestTransaction
    void messagesUnderPerInstanceLimitPass() {
        helper.createChannel("rl-inst-1", "Per-instance limit", null, null, null, null, null, 2, null, null, null, null, null, null, null, null, null, null, null);

        assertDoesNotThrow(() -> helper.sendMessage("rl-inst-1", "alice", "status", "1", null, null, null, null, null, null, null, null, null));
        assertDoesNotThrow(() -> helper.sendMessage("rl-inst-1", "alice", "status", "2", null, null, null, null, null, null, null, null, null));
    }

    @Test
    @TestTransaction
    void messageOverPerInstanceLimitIsRejectedWithClearError() {
        helper.createChannel("rl-inst-2", "Per-instance limit", null, null, null, null, null, 2, null, null, null, null, null, null, null, null, null, null, null);

        helper.sendMessage("rl-inst-2", "alice", "status", "1", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("rl-inst-2", "alice", "status", "2", null, null, null, null, null, null, null, null, null);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> helper.sendMessage("rl-inst-2", "alice", "status", "3", null, null, null, null, null, null, null, null, null), "message over per-instance limit should be rejected");

        String msg = ex.getMessage().toLowerCase();
        assertTrue(msg.contains("rate") || msg.contains("limit"),
                "error should mention rate limiting, was: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("alice"),
                "error should name the sender, was: " + ex.getMessage());
    }

    @Test
    @TestTransaction
    void perInstanceLimitIsIsolatedBetweenSenders() {
        // Per-instance limit = 2 but no per-channel limit
        helper.createChannel("rl-inst-3", "Per-instance limit", null, null, null, null, null, 2, null, null, null, null, null, null, null, null, null, null, null);

        // Alice sends 2 — at her limit
        helper.sendMessage("rl-inst-3", "alice", "status", "a1", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("rl-inst-3", "alice", "status", "a2", null, null, null, null, null, null, null, null, null);

        // Alice's 3rd is rejected
        assertThrows(IllegalStateException.class, () -> helper.sendMessage("rl-inst-3", "alice", "status", "a3", null, null, null, null, null, null, null, null, null), "alice should be rate-limited");

        // Bob still has his own quota — can send 2 freely
        assertDoesNotThrow(() -> helper.sendMessage("rl-inst-3", "bob", "status", "b1", null, null, null, null, null, null, null, null, null),
                "bob should have his own independent per-instance quota");
        assertDoesNotThrow(() -> helper.sendMessage("rl-inst-3", "bob", "status", "b2", null, null, null, null, null, null, null, null, null));
    }

    // =========================================================================
    // Unit — both limits independently enforced
    // =========================================================================

    @Test
    @TestTransaction
    void perChannelLimitCanBeHitBeforePerInstanceLimit() {
        // Channel limit = 3, instance limit = 5
        helper.createChannel("rl-both-1", "Both limits", null, null, null, null, 3, 5, null, null, null, null, null, null, null, null, null, null, null);

        helper.sendMessage("rl-both-1", "alice", "status", "1", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("rl-both-1", "alice", "status", "2", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("rl-both-1", "alice", "status", "3", null, null, null, null, null, null, null, null, null);

        // Channel limit hit (3) — even though alice's instance limit (5) is not reached
        assertThrows(IllegalStateException.class, () -> helper.sendMessage("rl-both-1", "alice", "status", "4", null, null, null, null, null, null, null, null, null), "per-channel limit should fire even when per-instance limit not reached");
    }

    @Test
    @TestTransaction
    void perInstanceLimitCanBeHitBeforePerChannelLimit() {
        // Channel limit = 10, instance limit = 2
        helper.createChannel("rl-both-2", "Both limits", null, null, null, null, 10, 2, null, null, null, null, null, null, null, null, null, null, null);

        helper.sendMessage("rl-both-2", "alice", "status", "1", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("rl-both-2", "alice", "status", "2", null, null, null, null, null, null, null, null, null);

        // Alice's instance limit hit — even though channel is only at 2 of 10
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> helper.sendMessage("rl-both-2", "alice", "status", "3", null, null, null, null, null, null, null, null, null), "per-instance limit should fire even when per-channel limit not reached");

        // Error should name alice (instance limit), not just the channel
        assertTrue(ex.getMessage().contains("alice"),
                "per-instance rejection should name the sender");
    }

    // =========================================================================
    // Unit — EVENT messages bypass rate limiting
    // =========================================================================

    @Test
    @TestTransaction
    void eventMessagesBypassRateLimit() {
        helper.createChannel("rl-evt-1", "Rate limited", null, null, null, null, 1, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("rl-evt-1", "alice", "status", "1", null, null, null, null, null, null, null, null, null);

        // Per-channel limit reached — but EVENT should bypass
        assertDoesNotThrow(
                () -> helper.sendMessage("rl-evt-1", "system", "event", null, null, null, null, null, null, null, null, null, null),
                "EVENT messages should bypass rate limiting");
    }

    // =========================================================================
    // Integration — set_channel_rate_limits
    // =========================================================================

    @Test
    @TestTransaction
    void setChannelRateLimitsAppliesLimitsToExistingChannel() {
        helper.createChannel("rl-scrl-1", "Open initially", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        // No limits — many messages pass
        helper.sendMessage("rl-scrl-1", "alice", "status", "1", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("rl-scrl-1", "alice", "status", "2", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("rl-scrl-1", "alice", "status", "3", null, null, null, null, null, null, null, null, null);

        // Apply rate limits
        ChannelDetail updated = helper.setChannelRateLimits("rl-scrl-1", 2, null);
        assertEquals(2, updated.rateLimitPerChannel(),
                "setChannelRateLimits should return ChannelDetail with updated rateLimitPerChannel");
        assertNull(updated.rateLimitPerInstance());
    }

    @Test
    @TestTransaction
    void setChannelRateLimitsToNullRemovesLimits() {
        helper.createChannel("rl-scrl-2", "Limited", null, null, null, null, 1, null, null, null, null, null, null, null, null, null, null, null, null);
        // Hit the limit
        helper.sendMessage("rl-scrl-2", "alice", "status", "1", null, null, null, null, null, null, null, null, null);
        assertThrows(IllegalStateException.class, () -> helper.sendMessage("rl-scrl-2", "alice", "status", "2", null, null, null, null, null, null, null, null, null));

        // Remove limits
        ChannelDetail updated = helper.setChannelRateLimits("rl-scrl-2", null, null);
        assertNull(updated.rateLimitPerChannel(), "removing limits should result in null rateLimitPerChannel");
    }

    @Test
    @TestTransaction
    void setChannelRateLimitsOnUnknownChannelThrows() {
        assertThrows(IllegalArgumentException.class, () -> helper.setChannelRateLimits("no-such-channel", 5, null));
    }

    // =========================================================================
    // Integration — ChannelDetail reflects rate limit config
    // =========================================================================

    @Test
    @TestTransaction
    void createChannelDetailIncludesRateLimits() {
        ChannelDetail detail = helper.createChannel("rl-det-1", "Rate limited", null, null, null, null, 10, 3, null, null, null, null, null, null, null, null, null, null, null);

        assertEquals(10, detail.rateLimitPerChannel());
        assertEquals(3, detail.rateLimitPerInstance());
    }

    @Test
    @TestTransaction
    void listChannelsIncludesRateLimits() {
        helper.createChannel("rl-det-2", "Rate limited", null, null, null, null, 5, 2, null, null, null, null, null, null, null, null, null, null, null);

        ChannelDetail found = helper.listChannels().stream()
                .filter(d -> "rl-det-2".equals(d.name()))
                .findFirst().orElseThrow();

        assertEquals(5, found.rateLimitPerChannel());
        assertEquals(2, found.rateLimitPerInstance());
    }

    // =========================================================================
    // E2E — multi-sender scenario with per-channel and per-instance limits
    // =========================================================================

    @Test
    @TestTransaction
    void e2eThreeSendersPerChannelLimit() {
        // Channel allows 4 messages/min total; each sender has no instance limit
        helper.createChannel("rl-e2e-1", "Shared budget", null, null, null, null, 4, null, null, null, null, null, null, null, null, null, null, null, null);

        // Alice sends 2, Bob sends 2 — channel budget exhausted
        helper.sendMessage("rl-e2e-1", "alice", "status", "a1", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("rl-e2e-1", "alice", "status", "a2", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("rl-e2e-1", "bob", "status", "b1", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("rl-e2e-1", "bob", "status", "b2", null, null, null, null, null, null, null, null, null);

        // Carol's message is rejected — channel budget gone
        assertThrows(IllegalStateException.class, () -> helper.sendMessage("rl-e2e-1", "carol", "status", "c1", null, null, null, null, null, null, null, null, null), "channel budget should be exhausted after alice and bob fill it");

        // Exactly 4 messages stored (not 5)
        var result = helper.checkMessages("rl-e2e-1", 0L, 20, null, null, null);
        assertEquals(4, result.size());
    }

    @Test
    @TestTransaction
    void e2ePerInstanceLimitDoesNotAffectOtherSenders() {
        // Per-instance limit = 2; no per-channel limit
        helper.createChannel("rl-e2e-2", "Per-instance only", null, null, null, null, null, 2, null, null, null, null, null, null, null, null, null, null, null);

        // Alice fills her quota
        helper.sendMessage("rl-e2e-2", "alice", "command", "a1", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("rl-e2e-2", "alice", "command", "a2", null, null, null, null, null, null, null, null, null);

        // Alice is blocked
        assertThrows(IllegalStateException.class, () -> helper.sendMessage("rl-e2e-2", "alice", "command", "a3", null, null, null, null, null, null, null, null, null));

        // Bob and Carol each have independent quotas and can send freely
        for (int i = 0; i < 2; i++) {
            final int n = i;
            assertDoesNotThrow(() -> helper.sendMessage("rl-e2e-2", "bob", "status", "b" + n, null, null, null, null, null, null, null, null, null));
            assertDoesNotThrow(() -> helper.sendMessage("rl-e2e-2", "carol", "status", "c" + n, null, null, null, null, null, null, null, null, null));
        }

        // 6 messages total: 2 alice + 2 bob + 2 carol
        var result = helper.checkMessages("rl-e2e-2", 0L, 20, null, null, null);
        assertEquals(6, result.size());
    }

    // =========================================================================
    // E2E — rate limits coexist with write ACL and admin controls
    // =========================================================================

    @Test
    @TestTransaction
    void e2eRateLimitAndWriteAclCoexist() {
        // Only alice can write, and there's a per-channel limit of 2
        helper.createChannel("rl-e2e-3", "ACL + rate limit", null, null, "alice", null, 2, null, null, null, null, null, null, null, null, null, null, null, null);

        // alice passes both checks for first 2 messages
        helper.sendMessage("rl-e2e-3", "alice", "status", "1", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("rl-e2e-3", "alice", "status", "2", null, null, null, null, null, null, null, null, null);

        // alice is now rate-limited
        assertThrows(IllegalStateException.class, () -> helper.sendMessage("rl-e2e-3", "alice", "status", "3", null, null, null, null, null, null, null, null, null));

        // bob is still rejected by write ACL (not rate limiting)
        IllegalStateException aclEx = assertThrows(IllegalStateException.class, () -> helper.sendMessage("rl-e2e-3", "bob", "status", "intrude", null, null, null, null, null, null, null, null, null));
        // ACL check fires before rate limit check — error should be about ACL, not rate limit
        assertTrue(aclEx.getMessage().contains("bob"),
                "bob should be rejected by ACL, error should name bob");
    }
}
