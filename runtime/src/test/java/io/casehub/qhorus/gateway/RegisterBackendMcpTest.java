package io.casehub.qhorus.gateway;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import java.util.UUID;

import io.casehub.platform.api.identity.ActorType;
import io.casehub.qhorus.api.gateway.ChannelBackend;
import io.casehub.qhorus.api.gateway.ChannelRef;
import io.casehub.qhorus.runtime.gateway.DuplicateParticipatingBackendException;
import io.casehub.qhorus.api.gateway.OutboundMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.casehub.qhorus.runtime.gateway.ChannelGateway;
import io.casehub.qhorus.runtime.mcp.QhorusMcpTools;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@QuarkusTest
class RegisterBackendMcpTest {

    @Inject QhorusMcpTools tools;
    @Inject ChannelGateway gateway;

    private UUID channelId;

    @BeforeEach
    @Transactional
    void setUp() {
        var detail = tools.createChannel("reg-back-1", "test", "append", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);
        channelId = detail.channelId();
    }

    @AfterEach
    @Transactional
    void tearDown() {
        try { tools.deleteChannel("reg-back-1", null, null); } catch (Exception ignored) {}
    }

    @Test
    void deregisterBackend_qhorusInternal_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> gateway.deregisterBackend(channelId, "qhorus-internal"));
    }

    @Test
    void registerBackend_duplicateParticipating_throws() {
        ChannelBackend first = stubBackend("first-participating");
        gateway.registerBackend(channelId, first, "human_participating");

        ChannelBackend second = stubBackend("second-participating");
        assertThrows(DuplicateParticipatingBackendException.class,
                () -> gateway.registerBackend(channelId, second, "human_participating"));
    }

    @Test
    void registerBackend_duplicateBackendId_isIdempotent() {
        ChannelBackend backend = stubBackend("idempotent-obs");
        gateway.registerBackend(channelId, backend, "human_observer");
        gateway.registerBackend(channelId, backend, "human_observer");

        long count = gateway.listBackends(channelId).stream()
                .filter(r -> "idempotent-obs".equals(r.backendId())).count();
        assertEquals(1, count, "duplicate registration should be idempotent");
    }

    @Test
    void registerBackend_unknownChannel_succeeds() {
        UUID unknownId = UUID.randomUUID();
        ChannelBackend backend = stubBackend("orphan");
        assertDoesNotThrow(
                () -> gateway.registerBackend(unknownId, backend, "human_observer"),
                "gateway allows registration on unknown channel (lazy init)");
    }

    private static ChannelBackend stubBackend(String id) {
        return new ChannelBackend() {
            @Override public String backendId() { return id; }
            @Override public ActorType actorType() { return ActorType.HUMAN; }
            @Override public void open(ChannelRef channel, Map<String, String> metadata) {}
            @Override public void post(ChannelRef channel, OutboundMessage message) {}
            @Override public void close(ChannelRef channel) {}
        };
    }
}
