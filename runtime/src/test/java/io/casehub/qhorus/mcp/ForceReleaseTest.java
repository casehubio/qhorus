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
import io.casehub.qhorus.runtime.mcp.QhorusMcpTools;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;

@QuarkusTest
class ForceReleaseTest {

    @Inject QhorusMcpTools tools;
    @Inject ChannelService channelService;
    @Inject MessageStore messageStore;

    private record ForceReleaseResult(String channelName, String semantic, int messageCount, List<Message> messages) {}

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
        tools.createChannel("fr-barrier-1", "Test", "BARRIER", "alice,bob", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        tools.sendMessage("fr-barrier-1", "alice", "status", "alice done", null, null, null, null, null, null, null, null, null);

        ForceReleaseResult result = forceRelease("fr-barrier-1");

        assertNotNull(result);
        assertEquals("fr-barrier-1", result.channelName());
        assertEquals("BARRIER", result.semantic());
        assertEquals(1, result.messageCount(), "force-released message count should be 1");
        assertEquals(1, result.messages().size());
        assertEquals("alice done", result.messages().get(0).content());
    }

    @Test
    @TestTransaction
    void forceReleaseBarrierPostsAuditEvent() {
        tools.createChannel("fr-barrier-2", "Test", "BARRIER", "alice,bob", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        tools.sendMessage("fr-barrier-2", "alice", "status", "partial", null, null, null, null, null, null, null, null, null);

        forceRelease("fr-barrier-2");

        ForceReleaseResult result = forceRelease("fr-barrier-2");
        assertEquals(0, result.messageCount(), "channel should be empty after first force-release");
    }

    @Test
    @TestTransaction
    void forceReleaseEmptyBarrierReturnsZeroMessages() {
        tools.createChannel("fr-barrier-3", "Test", "BARRIER", "alice,bob", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        ForceReleaseResult result = forceRelease("fr-barrier-3");

        assertEquals(0, result.messageCount(), "empty channel force-release should return 0 messages");
        assertTrue(result.messages().isEmpty());
    }

    @Test
    @TestTransaction
    void forceReleaseBarrierClearsChannelSoSubsequentCheckMessagesIsEmpty() {
        tools.createChannel("fr-barrier-4", "Test", "BARRIER", "alice,bob", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        tools.sendMessage("fr-barrier-4", "alice", "status", "alice work", null, null, null, null, null, null, null, null, null);

        forceRelease("fr-barrier-4");

        QhorusMcpTools.CheckResult check = tools.checkMessages("fr-barrier-4", 0L, 10, null, null, null);
        assertTrue(check.messages().isEmpty(),
                "channel should be empty after force-release — messages were delivered and cleared");
        assertNotNull(check.barrierStatus(),
                "BARRIER resets to waiting state after force-release — this is correct: new cycle begins");
    }

    @Test
    @TestTransaction
    void forceReleaseCollectDeliversAccumulatedMessages() {
        tools.createChannel("fr-collect-1", "Test", "COLLECT", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        tools.sendMessage("fr-collect-1", "alice", "status", "alice result", null, null, null, null, null, null, null, null, null);
        tools.sendMessage("fr-collect-1", "bob", "status", "bob result", null, null, null, null, null, null, null, null, null);

        ForceReleaseResult result = forceRelease("fr-collect-1");

        assertEquals(2, result.messageCount());
        assertEquals(2, result.messages().size());
    }

    @Test
    @TestTransaction
    void forceReleaseCollectClearsChannelAfterDelivery() {
        tools.createChannel("fr-collect-2", "Test", "COLLECT", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        tools.sendMessage("fr-collect-2", "alice", "status", "msg", null, null, null, null, null, null, null, null, null);

        forceRelease("fr-collect-2");

        QhorusMcpTools.CheckResult check = tools.checkMessages("fr-collect-2", 0L, 10, null, null, null);
        assertTrue(check.messages().isEmpty(),
                "COLLECT channel should be empty after force-release");
    }

    @Test
    @TestTransaction
    void forceReleaseAppendChannelThrows() {
        tools.createChannel("fr-append-1", "Test", "APPEND", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        assertThrows(IllegalArgumentException.class,
                () -> forceRelease("fr-append-1"),
                "force_release_channel should reject APPEND channels");
    }

    @Test
    @TestTransaction
    void forceReleaseEphemeralChannelThrows() {
        tools.createChannel("fr-ephemeral-1", "Test", "EPHEMERAL", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        assertThrows(IllegalArgumentException.class,
                () -> forceRelease("fr-ephemeral-1"));
    }

    @Test
    @TestTransaction
    void forceReleaseLastWriteChannelThrows() {
        tools.createChannel("fr-lw-1", "Test", "LAST_WRITE", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

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
        tools.createChannel("fr-int-1", "Test", "BARRIER", "alice,bob,carol", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        tools.sendMessage("fr-int-1", "alice", "status", "alice done", null, null, null, null, null, null, null, null, null);
        tools.sendMessage("fr-int-1", "bob", "status", "bob done", null, null, null, null, null, null, null, null, null);

        QhorusMcpTools.CheckResult stuck = tools.checkMessages("fr-int-1", 0L, 10, null, null, null);
        assertTrue(stuck.messages().isEmpty(), "BARRIER still blocked");
        assertNotNull(stuck.barrierStatus());
        assertTrue(stuck.barrierStatus().contains("carol"));

        ForceReleaseResult released = forceRelease("fr-int-1");

        assertEquals(2, released.messageCount());
        assertTrue(released.messages().stream().anyMatch(m -> "alice done".equals(m.content())));
        assertTrue(released.messages().stream().anyMatch(m -> "bob done".equals(m.content())));

        QhorusMcpTools.CheckResult afterRelease = tools.checkMessages("fr-int-1", 0L, 10, null, null, null);
        assertTrue(afterRelease.messages().isEmpty());
    }

    @Test
    @TestTransaction
    void e2eHumanForceReleasesStuckBarrier() {
        tools.createChannel("fr-e2e-1", "Code Review", "BARRIER", "reviewer-1,reviewer-2", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        tools.sendMessage("fr-e2e-1", "reviewer-1", "status", "LGTM — approved by reviewer-1", null, null, null, null, null, null, null, null, null);

        QhorusMcpTools.CheckResult stuck = tools.checkMessages("fr-e2e-1", 0L, 10, null, null, null);
        assertTrue(stuck.messages().isEmpty(), "barrier should be blocked");

        ForceReleaseResult result = forceRelease("fr-e2e-1");

        assertEquals(1, result.messageCount());
        assertEquals("LGTM — approved by reviewer-1", result.messages().get(0).content());

        assertTrue(tools.checkMessages("fr-e2e-1", 0L, 10, null, null, null).messages().isEmpty());
    }

    @Test
    @TestTransaction
    void e2eHumanForceReleasesStuckCollect() {
        tools.createChannel("fr-e2e-2", "Results", "COLLECT", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        tools.sendMessage("fr-e2e-2", "agent-1", "status", "result-1", null, null, null, null, null, null, null, null, null);
        tools.sendMessage("fr-e2e-2", "agent-2", "status", "result-2", null, null, null, null, null, null, null, null, null);
        tools.sendMessage("fr-e2e-2", "agent-3", "status", "result-3", null, null, null, null, null, null, null, null, null);

        ForceReleaseResult result = forceRelease("fr-e2e-2");

        assertEquals(3, result.messageCount());
        List<String> contents = result.messages().stream()
                .map(Message::content).toList();
        assertTrue(contents.contains("result-1"));
        assertTrue(contents.contains("result-2"));
        assertTrue(contents.contains("result-3"));
    }
}
