package io.casehub.qhorus.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import io.casehub.qhorus.api.gateway.MessageObserver;
import io.casehub.qhorus.api.gateway.MessageReceivedEvent;
import io.casehub.qhorus.api.message.MessageType;
import io.quarkus.websockets.next.WebSocketConnection;

class WebSocketMessageObserverTest {

    private WebSocketConnectionRegistry registry;
    private WebSocketMessageObserver observer;

    @BeforeEach
    void setUp() {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        registry = new WebSocketConnectionRegistry();
        observer = new WebSocketMessageObserver(mapper, registry);
    }

    @Test
    void scopeIsCluster() {
        assertThat(observer.scope()).isEqualTo(MessageObserver.Scope.CLUSTER);
    }

    @Test
    void pushesToSubscribedConnection() {
        UUID channelId = UUID.randomUUID();
        WebSocketConnection conn = mock(WebSocketConnection.class);
        registry.subscribe(channelId, conn);

        observer.onMessage(new MessageReceivedEvent(
                "test-channel", channelId, "t1",
                MessageType.STATUS, "agent-1", null,
                Instant.now(), "hello", null));

        verify(conn).sendTextAndAwait(anyString());
    }

    @Test
    void skipsChannelsWithNoSubscribers() {
        UUID channelId = UUID.randomUUID();

        observer.onMessage(new MessageReceivedEvent(
                "test-channel", channelId, "t1",
                MessageType.STATUS, "agent-1", null,
                Instant.now(), "hello", null));
        // No exception, no crash
    }

    @Test
    void pushesCloudEventJson() {
        UUID channelId = UUID.randomUUID();
        WebSocketConnection conn = mock(WebSocketConnection.class);
        registry.subscribe(channelId, conn);

        observer.onMessage(new MessageReceivedEvent(
                "test-channel", channelId, "t1",
                MessageType.COMMAND, "agent-1", "corr-1",
                Instant.now(), "do it", "general"));

        var captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(conn).sendTextAndAwait(captor.capture());
        String json = captor.getValue();
        assertThat(json).contains("COMMAND");
        assertThat(json).contains(channelId.toString());
        assertThat(json).contains("agent-1");
    }

    @Test
    void failedSendDoesNotAffectOtherConnections() {
        UUID channelId = UUID.randomUUID();
        WebSocketConnection bad = mock(WebSocketConnection.class);
        doThrow(new RuntimeException("connection closed")).when(bad).sendTextAndAwait(anyString());
        WebSocketConnection good = mock(WebSocketConnection.class);

        registry.subscribe(channelId, bad);
        registry.subscribe(channelId, good);

        observer.onMessage(new MessageReceivedEvent(
                "test-channel", channelId, "t1",
                MessageType.STATUS, "agent-1", null,
                Instant.now(), "hello", null));

        verify(good).sendTextAndAwait(anyString());
    }
}
