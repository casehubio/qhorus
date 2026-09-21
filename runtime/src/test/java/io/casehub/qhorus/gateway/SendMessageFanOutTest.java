package io.casehub.qhorus.gateway;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.gateway.ChannelBackend;
import io.casehub.qhorus.api.gateway.ChannelRef;
import io.casehub.qhorus.api.gateway.OutboundMessage;
import io.casehub.qhorus.runtime.gateway.ChannelGateway;
import io.casehub.qhorus.testing.QhorusTestHelper;
import io.quarkus.test.junit.QuarkusTest;

/**
 * Integration tests verifying that ChannelGateway is correctly wired into the MCP tools:
 * - createChannel calls initChannel (qhorus-internal backend is registered)
 * - deleteChannel calls closeChannel (all backends deregistered)
 * - sendMessage calls fanOut (external backends receive the message)
 * - listBackends and deregisterBackend MCP tools work end-to-end
 *
 * Refs #131 #138
 */
@QuarkusTest
class SendMessageFanOutTest {

    /** Local recording backend — avoids circular dependency on casehub-qhorus-testing. */
    static class RecordingBackend implements ChannelBackend {
        private final String id;
        private final ActorType actorType;
        private final List<OutboundMessage> posts = Collections.synchronizedList(new ArrayList<>());

        RecordingBackend(String id, ActorType actorType) {
            this.id = id;
            this.actorType = actorType;
        }

        @Override public String backendId() { return id; }
        @Override public ActorType actorType() { return actorType; }
        @Override public void open(ChannelRef channel, Map<String, String> metadata) {}
        @Override public void post(ChannelRef channel, OutboundMessage message) { posts.add(message); }
        @Override public void close(ChannelRef channel) {}

        List<OutboundMessage> posts() { return Collections.unmodifiableList(posts); }
    }

    @Inject QhorusTestHelper helper;

    @Inject
    ChannelGateway gateway;

    @BeforeEach
    @Transactional
    void setUp() {
        helper.createChannel("fanout-1", "test", "append", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
    }

    @AfterEach
    @Transactional
    void tearDown() {
        // Use force=true so messages created during tests are deleted too
        helper.deleteChannel("fanout-1", true, null);
    }

    @Test
    void sendMessage_stillWorks() {
        var result = helper.sendMessage("fanout-1", "agent-a", "command",
                "do the thing", null, null, null, null, null, null, null, null, null);
        assertNotNull(result);
        assertNotNull(result.messageId()); // message was persisted successfully
    }

    @Test
    void sendMessage_fansOutToObserver() throws Exception {
        var ch = helper.listChannels().stream()
                .filter(c -> "fanout-1".equals(c.name())).findFirst().orElseThrow();
        RecordingBackend observer = new RecordingBackend("test-obs", ActorType.HUMAN);
        gateway.registerBackend(ch.channelId(), observer, "human_observer");

        helper.sendMessage("fanout-1", "agent-a", "event", null, null, null, null, null, null, null, null, null, null);

        Thread.sleep(300);
        assertEquals(1, observer.posts().size());
        assertNull(observer.posts().get(0).content());
    }

    @Test
    @Transactional
    void createChannel_autoRegistersQhorusInternal() {
        var ch = helper.listChannels().stream()
                .filter(c -> "fanout-1".equals(c.name())).findFirst().orElseThrow();
        var backends = gateway.listBackends(ch.channelId());
        assertEquals(1, backends.size());
        assertEquals("qhorus-internal", backends.get(0).backendId());
    }

    @Test
    @Transactional
    void deleteChannel_deregistersAllBackends() {
        helper.createChannel("fanout-2", "test2", "append", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        var ch = helper.listChannels().stream()
                .filter(c -> "fanout-2".equals(c.name())).findFirst().orElseThrow();
        UUID channelId = ch.channelId();
        helper.deleteChannel("fanout-2", true, null);
        assertTrue(gateway.listBackends(channelId).isEmpty());
    }

    @Test
    void listBackends_returnsQhorusInternal() {
        var ch = helper.listChannels().stream()
                .filter(c -> "fanout-1".equals(c.name())).findFirst().orElseThrow();
        var result = gateway.listBackends(ch.channelId());
        assertEquals(1, result.size());
        assertEquals("qhorus-internal", result.get(0).backendId());
        assertEquals("agent", result.get(0).actorType().name().toLowerCase());
    }

    @Test
    @Transactional
    void deregisterBackend_removesObserver() {
        var ch = helper.listChannels().stream()
                .filter(c -> "fanout-1".equals(c.name())).findFirst().orElseThrow();
        RecordingBackend obs = new RecordingBackend("to-remove", ActorType.HUMAN);
        gateway.registerBackend(ch.channelId(), obs, "human_observer");

        gateway.deregisterBackend(ch.channelId(), "to-remove");

        var backends = gateway.listBackends(ch.channelId());
        assertEquals(1, backends.size());
        assertEquals("qhorus-internal", backends.get(0).backendId());
    }
}
