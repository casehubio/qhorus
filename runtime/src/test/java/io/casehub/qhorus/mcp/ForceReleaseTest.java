package io.casehub.qhorus.mcp;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import jakarta.inject.Inject;

import org.junit.jupiter.api.Test;

import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.channel.ChannelSemantic;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.message.MessageType;
import io.casehub.qhorus.api.store.MessageStore;
import io.casehub.qhorus.api.store.query.MessageQuery;
import io.casehub.qhorus.runtime.channel.ChannelService;
import io.casehub.qhorus.testing.QhorusTestHelper;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class ForceReleaseTest {

    @Inject QhorusTestHelper helper;
    @Inject ChannelService channelService;
    @Inject MessageStore messageStore;

    private record ForceReleaseResult(String channelName, String semantic, int messageCount, List<Message> messages) {
        boolean isEmpty() { return messages == null || messages.isEmpty(); }
        int size() { return messages != null ? messages.size() : 0; }
        Message get(int i) { return messages.get(i); }
        java.util.stream.Stream<Message> stream() { return messages != null ? messages.stream() : java.util.stream.Stream.empty(); }
    }

    private ForceReleaseResult forceRelease(String channelName) {
        Channel ch = channelService.findByName(channelName)
                .orElseThrow(() -> new IllegalArgumentException("Channel not found: " + channelName));
        if (ch.semantic() != ChannelSemantic.BARRIER && ch.semantic() != ChannelSemantic.COLLECT) {
            throw new IllegalArgumentException(
                    "force_release_channel only applies to BARRIER and COLLECT channels, not " + ch.semantic().name());
        }
        List<Message> messages = messageStore.scan(
                MessageQuery.builder().channelId(ch.id())
                        .excludeTypes(List.of(MessageType.EVENT)).build());
        messageStore.deleteNonEvent(ch.id());
        return new ForceReleaseResult(ch.name(), ch.semantic().name(), messages.size(), messages);
    }

    @Test
    @TestTransaction
    void forceReleaseBarrierDeliversMessagesBeforeAllContributorsWrite() {
        helper.createChannel("fr-barrier-1", "Test", "BARRIER", "alice,bob", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("fr-barrier-1", "alice", "status", "alice done", null, null, null, null, null, null, null, null, null);

        ForceReleaseResult result = forceRelease("fr-barrier-1");

        assertNotNull(result);
        assertEquals("fr-barrier-1", result.channelName());
        assertEquals("BARRIER", result.semantic());
        assertEquals(1, result.messageCount(), "force-released message count should be 1");
        assertEquals(1, result.size());
        assertEquals("alice done", result.get(0).content());
    }

    @Test
    @TestTransaction
    void forceReleaseBarrierPostsAuditEvent() {
        helper.createChannel("fr-barrier-2", "Test", "BARRIER", "alice,bob", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("fr-barrier-2", "alice", "status", "partial", null, null, null, null, null, null, null, null, null);

        forceRelease("fr-barrier-2");

        ForceReleaseResult result = forceRelease("fr-barrier-2");
        assertEquals(0, result.messageCount(), "channel should be empty after first force-release");
    }

    @Test
    @TestTransaction
    void forceReleaseEmptyBarrierReturnsZeroMessages() {
        helper.createChannel("fr-barrier-3", "Test", "BARRIER", "alice,bob", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        ForceReleaseResult result = forceRelease("fr-barrier-3");

        assertEquals(0, result.messageCount(), "empty channel force-release should return 0 messages");
        assertTrue(result.isEmpty());
    }

    @Test
    @TestTransaction
    void forceReleaseBarrierClearsChannelSoSubsequentCheckMessagesIsEmpty() {
        helper.createChannel("fr-barrier-4", "Test", "BARRIER", "alice,bob", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("fr-barrier-4", "alice", "status", "alice work", null, null, null, null, null, null, null, null, null);

        forceRelease("fr-barrier-4");

        var check = helper.checkMessages("fr-barrier-4", 0L, 10, null, null, null);
        assertTrue(check.isEmpty(),
                "channel should be empty after force-release — messages were delivered and cleared");
        assertNotNull(check.barrierStatus(),
                "BARRIER resets to waiting state after force-release — this is correct: new cycle begins");
    }

    @Test
    @TestTransaction
    void forceReleaseCollectDeliversAccumulatedMessages() {
        helper.createChannel("fr-collect-1", "Test", "COLLECT", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("fr-collect-1", "alice", "status", "alice result", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("fr-collect-1", "bob", "status", "bob result", null, null, null, null, null, null, null, null, null);

        ForceReleaseResult result = forceRelease("fr-collect-1");

        assertEquals(2, result.messageCount());
        assertEquals(2, result.size());
    }

    @Test
    @TestTransaction
    void forceReleaseCollectClearsChannelAfterDelivery() {
        helper.createChannel("fr-collect-2", "Test", "COLLECT", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("fr-collect-2", "alice", "status", "msg", null, null, null, null, null, null, null, null, null);

        forceRelease("fr-collect-2");

        var check = helper.checkMessages("fr-collect-2", 0L, 10, null, null, null);
        assertTrue(check.isEmpty(),
                "COLLECT channel should be empty after force-release");
    }

    @Test
    @TestTransaction
    void forceReleaseAppendChannelThrows() {
        helper.createChannel("fr-append-1", "Test", "APPEND", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        assertThrows(IllegalArgumentException.class,
                () -> forceRelease("fr-append-1"),
                "force_release_channel should reject APPEND channels");
    }

    @Test
    @TestTransaction
    void forceReleaseEphemeralChannelThrows() {
        helper.createChannel("fr-ephemeral-1", "Test", "EPHEMERAL", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        assertThrows(IllegalArgumentException.class,
                () -> forceRelease("fr-ephemeral-1"));
    }

    @Test
    @TestTransaction
    void forceReleaseLastWriteChannelThrows() {
        helper.createChannel("fr-lw-1", "Test", "LAST_WRITE", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        assertThrows(IllegalArgumentException.class,
                () -> forceRelease("fr-lw-1"));
    }

    @Test
    @TestTransaction
    void forceReleaseUnknownChannelThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> forceRelease("no-such-channel"));
    }

    @Test
    @TestTransaction
    void integrationBarrierStuckThenForceReleased() {
        helper.createChannel("fr-int-1", "Test", "BARRIER", "alice,bob,carol", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        helper.sendMessage("fr-int-1", "alice", "status", "alice done", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("fr-int-1", "bob", "status", "bob done", null, null, null, null, null, null, null, null, null);

        var stuck = helper.checkMessages("fr-int-1", 0L, 10, null, null, null);
        assertTrue(stuck.isEmpty(), "BARRIER still blocked");
        assertNotNull(stuck.barrierStatus());
        assertTrue(stuck.barrierStatus().contains("carol"));

        ForceReleaseResult released = forceRelease("fr-int-1");

        assertEquals(2, released.messageCount());
        assertTrue(released.stream().anyMatch(m -> "alice done".equals(m.content())));
        assertTrue(released.stream().anyMatch(m -> "bob done".equals(m.content())));

        var afterRelease = helper.checkMessages("fr-int-1", 0L, 10, null, null, null);
        assertTrue(afterRelease.isEmpty());
    }

    @Test
    @TestTransaction
    void e2eHumanForceReleasesStuckBarrier() {
        helper.createChannel("fr-e2e-1", "Code Review", "BARRIER", "reviewer-1,reviewer-2", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        helper.sendMessage("fr-e2e-1", "reviewer-1", "status", "LGTM — approved by reviewer-1", null, null, null, null, null, null, null, null, null);

        var stuck = helper.checkMessages("fr-e2e-1", 0L, 10, null, null, null);
        assertTrue(stuck.isEmpty(), "barrier should be blocked");

        ForceReleaseResult result = forceRelease("fr-e2e-1");

        assertEquals(1, result.messageCount());
        assertEquals("LGTM — approved by reviewer-1", result.get(0).content());

        assertTrue(helper.checkMessages("fr-e2e-1", 0L, 10, null, null, null).isEmpty());
    }

    @Test
    @TestTransaction
    void e2eHumanForceReleasesStuckCollect() {
        helper.createChannel("fr-e2e-2", "Results", "COLLECT", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        helper.sendMessage("fr-e2e-2", "agent-1", "status", "result-1", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("fr-e2e-2", "agent-2", "status", "result-2", null, null, null, null, null, null, null, null, null);
        helper.sendMessage("fr-e2e-2", "agent-3", "status", "result-3", null, null, null, null, null, null, null, null, null);

        ForceReleaseResult result = forceRelease("fr-e2e-2");

        assertEquals(3, result.messageCount());
        List<String> contents = result.stream()
                .map(Message::content).toList();
        assertTrue(contents.contains("result-1"));
        assertTrue(contents.contains("result-2"));
        assertTrue(contents.contains("result-3"));
    }
}
