package io.casehub.qhorus.mcp;

import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.store.MessageStore;
import io.casehub.qhorus.api.store.query.MessageQuery;
import io.casehub.qhorus.runtime.channel.ChannelService;
import io.casehub.qhorus.runtime.message.TopicService;
import io.casehub.qhorus.testing.QhorusTestHelper;
import io.casehub.qhorus.testing.QhorusTestHelper.ChannelDigest;
import io.casehub.qhorus.testing.QhorusTestHelper.ArtefactDetail;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class ChannelDigestTest {

    @Inject QhorusTestHelper helper;
    @Inject TopicService topicService;
    @Inject ChannelService channelService;
    @Inject MessageStore messageStore;

    // -------------------------------------------------------------------------
    // Unit — field correctness
    // -------------------------------------------------------------------------

    @Test
    @TestTransaction
    void digestEmptyChannelReturnsZerosAndNulls() {
        helper.createChannel("cd-empty-1", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        ChannelDigest digest = helper.channelDigest("cd-empty-1", null);

        assertNotNull(digest);
        assertEquals("cd-empty-1", digest.channelName());
        assertEquals(0L, digest.messageCount());
        assertTrue(digest.senderBreakdown().isEmpty());
        assertTrue(digest.typeBreakdown().isEmpty());
        assertEquals(0, digest.artefactRefCount());
        assertNull(digest.oldestMessageAt());
        assertNull(digest.newestMessageAt());
        assertTrue(digest.recentMessages().isEmpty());
    }

    @Test
    @TestTransaction
    void digestCorrectlyCountsMessages() {
        helper.createChannel("cd-count-1", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("cd-count-1", "alice", "command", "msg1", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("cd-count-1", "bob", "status", "msg2", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("cd-count-1", "carol", "status", "msg3", null, null, null, null, null, null, null, null, null);

        ChannelDigest digest = helper.channelDigest("cd-count-1", null);

        assertEquals(3L, digest.messageCount());
    }

    @Test
    @TestTransaction
    void digestSenderBreakdownIsCorrect() {
        helper.createChannel("cd-sender-1", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("cd-sender-1", "alice", "status", "a", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("cd-sender-1", "alice", "status", "b", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("cd-sender-1", "bob", "status", "c", null, null, null, null, null, null, null, null, null);

        ChannelDigest digest = helper.channelDigest("cd-sender-1", null);

        assertEquals(2, digest.senderBreakdown().get("alice"));
        assertEquals(1, digest.senderBreakdown().get("bob"));
    }

    @Test
    @TestTransaction
    void digestTypeBreakdownIsCorrect() {
        helper.createChannel("cd-type-1", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("cd-type-1", "alice", "query", "q", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("cd-type-1", "bob", "status", "a", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("cd-type-1", "bob", "status", "b", null, null, null, null, null, null, null, null, null);

        ChannelDigest digest = helper.channelDigest("cd-type-1", null);

        assertEquals(1, digest.typeBreakdown().get("QUERY"));
        assertEquals(2, digest.typeBreakdown().get("STATUS"));
    }

    @Test
    @TestTransaction
    void digestArtefactRefCountIsCorrect() {
        helper.createChannel("cd-refs-1", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        ArtefactDetail a1 = helper.shareArtefact("cd-art-1", "d", "alice", "c", false, true);
        ArtefactDetail a2 = helper.shareArtefact("cd-art-2", "d", "alice", "c", false, true);

        // Message 1 references both artefacts
        helper.sendMessage("cd-refs-1", "alice", "status", "msg", null, null, null, List.of(a1.artefactId().toString(), a2.artefactId().toString()), null, null, null, null, null);
        // Message 2 references artefact 1 again (same UUID, not double-counted)
        helper.sendMessage("cd-refs-1", "bob", "status", "msg2", null, null, null, List.of(a1.artefactId().toString()), null, null, null, null, null);

        ChannelDigest digest = helper.channelDigest("cd-refs-1", null);

        assertEquals(2, digest.artefactRefCount(),
                "distinct artefact UUIDs across all messages: 2");
    }

    @Test
    @TestTransaction
    void digestRecentMessagesRespectLimit() {
        helper.createChannel("cd-limit-1", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        for (int i = 0; i < 8; i++) {
            helper.sendMessage("cd-limit-1", "alice", "status", "msg" + i, null, null, null, null, null, null, null, null, null);
        }

        ChannelDigest digest = helper.channelDigest("cd-limit-1", 3);

        assertEquals(3, digest.recentMessages().size(), "recentMessages should be limited to 3");
        assertEquals(8L, digest.messageCount(), "total count should still be 8");
    }

    @Test
    @TestTransaction
    void digestContentTruncatedAt120Chars() {
        helper.createChannel("cd-trunc-1", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        String longContent = "x".repeat(200);
        helper.sendMessage("cd-trunc-1", "alice", "status", longContent, null, null, null, null, null, null, null, null, null);

        ChannelDigest digest = helper.channelDigest("cd-trunc-1", null);

        String preview = digest.recentMessages().get(0).contentPreview();
        assertTrue(preview.length() <= 121, "preview should not exceed 120 chars + ellipsis");
        assertTrue(preview.endsWith("…"), "preview should end with ellipsis when truncated");
    }

    @Test
    @TestTransaction
    void digestContentNotTruncatedWhenShort() {
        helper.createChannel("cd-trunc-2", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("cd-trunc-2", "alice", "status", "short", null, null, null, null, null, null, null, null, null);

        ChannelDigest digest = helper.channelDigest("cd-trunc-2", null);

        assertEquals("short", digest.recentMessages().get(0).contentPreview());
    }

    @Test
    @TestTransaction
    void digestOldestAndNewestTimestampsPresent() {
        helper.createChannel("cd-ts-1", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("cd-ts-1", "alice", "status", "first", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("cd-ts-1", "bob", "status", "last", null, null, null, null, null, null, null, null, null);

        ChannelDigest digest = helper.channelDigest("cd-ts-1", null);

        assertNotNull(digest.oldestMessageAt());
        assertNotNull(digest.newestMessageAt());
    }

    @Test
    @TestTransaction
    void digestReflectsPausedState() {
        helper.createChannel("cd-paused-1", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.pauseChannel("cd-paused-1", null);

        ChannelDigest digest = helper.channelDigest("cd-paused-1", null);

        assertTrue(digest.paused(), "digest should reflect paused state");
    }

    @Test
    @TestTransaction
    void digestUnknownChannelThrows() {
        assertThrows(IllegalArgumentException.class, () -> helper.channelDigest("no-such-channel", null));
    }

    // -------------------------------------------------------------------------
    // Integration — mixed senders and types
    // -------------------------------------------------------------------------

    @Test
    @TestTransaction
    void integrationDigestFullMixedChannel() {
        helper.createChannel("cd-int-1", "Work Channel", "APPEND", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("cd-int-1", "alice", "command", "task request", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("cd-int-1", "bob", "status", "bob's response", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("cd-int-1", "alice", "status", "alice status", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("cd-int-1", "carol", "status", "carol status", null, null, null, null, null, null, null, null, null);

        ChannelDigest digest = helper.channelDigest("cd-int-1", 10);

        assertEquals(4L, digest.messageCount());
        assertEquals(2, digest.senderBreakdown().get("alice"));
        assertEquals(1, digest.senderBreakdown().get("bob"));
        assertEquals(1, digest.senderBreakdown().get("carol"));
        assertEquals(1, digest.typeBreakdown().get("COMMAND"));
        assertEquals(3, digest.typeBreakdown().get("STATUS"));
        assertEquals(4, digest.recentMessages().size());
        assertNotNull(digest.oldestMessageAt());
        assertNotNull(digest.newestMessageAt());
    }

    // -------------------------------------------------------------------------
    // E2E — human reviews channel state before intervening
    // -------------------------------------------------------------------------

    @Test
    @TestTransaction
    void e2eHumanReviewsDigestBeforeForceRelease() {
        helper.createChannel("cd-e2e-1", "Review Channel", "BARRIER", "alice,bob", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        helper.sendMessage("cd-e2e-1", "alice", "status", "Alice's detailed review: all good", null, null, null, null, null, null, null, null, null);

        // Human calls get_channel_digest to understand state before intervening
        ChannelDigest digest = helper.channelDigest("cd-e2e-1", 5);

        assertEquals(1L, digest.messageCount());
        assertEquals(1, digest.senderBreakdown().get("alice"));
        assertEquals("BARRIER", digest.semantic());
        assertFalse(digest.paused());
        assertEquals("Alice's detailed review: all good",
                digest.recentMessages().get(0).contentPreview());

        // Human decides to force-release based on digest
        Channel frCh = channelService.findByName("cd-e2e-1").orElseThrow();
        List<Message> released = messageStore.scan(
                MessageQuery.builder().channelId(frCh.id())
                        .excludeTypes(List.of(MessageType.EVENT)).build());
        messageStore.deleteNonEvent(frCh.id());
        assertEquals(1, released.size());
    }

    @Test
    @TestTransaction
    void digestEmptyChannelHasEmptyTopicBreakdown() {
        helper.createChannel("cd-topic-empty", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        ChannelDigest digest = helper.channelDigest("cd-topic-empty", null);

        assertNotNull(digest.topicBreakdown());
        assertTrue(digest.topicBreakdown().isEmpty());
    }

    @Test
    @TestTransaction
    void digestShowsTopicBreakdownWithCounts() {
        helper.createChannel("cd-topic-count", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("cd-topic-count", "alice", "status", "msg1", null, null, null, null, null, null, null, null, "design");
        helper.sendMessage("cd-topic-count", "bob", "status", "msg2", null, null, null, null, null, null, null, null, "design");
        helper.sendMessage("cd-topic-count", "carol", "status", "msg3", null, null, null, null, null, null, null, null, "testing");

        ChannelDigest digest = helper.channelDigest("cd-topic-count", null);

        assertNotNull(digest.topicBreakdown());
        assertFalse(digest.topicBreakdown().isEmpty());

        var designTopic = digest.topicBreakdown().stream()
                                .filter(t -> "design".equals(t.name())).findFirst().orElseThrow();
        assertEquals(2, designTopic.messageCount());
        assertFalse(designTopic.resolved());

        var testingTopic = digest.topicBreakdown().stream()
                                 .filter(t -> "testing".equals(t.name())).findFirst().orElseThrow();
        assertEquals(1, testingTopic.messageCount());
    }

    @Test
    @TestTransaction
    void digestShowsResolvedTopicStatus() {
        helper.createChannel("cd-topic-resolved", "Test", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("cd-topic-resolved", "alice", "status", "msg1", null, null, null, null, null, null, null, null, "review");
        Channel ch = channelService.findByName("cd-topic-resolved").orElseThrow();
        topicService.resolve(ch.id(), "review", "alice");

        ChannelDigest digest = helper.channelDigest("cd-topic-resolved", null);

        var reviewTopic = digest.topicBreakdown().stream()
                                .filter(t -> "review".equals(t.name())).findFirst().orElseThrow();
        assertTrue(reviewTopic.resolved());
        assertNotNull(reviewTopic.resolvedAt());
    }


}
