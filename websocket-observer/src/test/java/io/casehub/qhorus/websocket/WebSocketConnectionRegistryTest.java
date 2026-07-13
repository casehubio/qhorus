package io.casehub.qhorus.websocket;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.quarkus.websockets.next.WebSocketConnection;

class WebSocketConnectionRegistryTest {

    private WebSocketConnectionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new WebSocketConnectionRegistry();
    }

    @Test
    void subscribeAndLookup() {
        UUID channelId = UUID.randomUUID();
        WebSocketConnection conn = mock(WebSocketConnection.class);

        registry.subscribe(channelId, conn);

        assertThat(registry.connections(channelId)).containsExactly(conn);
    }

    @Test
    void unsubscribeRemoves() {
        UUID channelId = UUID.randomUUID();
        WebSocketConnection conn = mock(WebSocketConnection.class);

        registry.subscribe(channelId, conn);
        registry.unsubscribe(channelId, conn);

        assertThat(registry.connections(channelId)).isEmpty();
    }

    @Test
    void unsubscribeLastConnectionCleansUpMap() {
        UUID channelId = UUID.randomUUID();
        WebSocketConnection conn = mock(WebSocketConnection.class);

        registry.subscribe(channelId, conn);
        registry.unsubscribe(channelId, conn);

        assertThat(registry.channelCount()).isZero();
    }

    @Test
    void multipleConnectionsPerChannel() {
        UUID channelId = UUID.randomUUID();
        WebSocketConnection c1 = mock(WebSocketConnection.class);
        WebSocketConnection c2 = mock(WebSocketConnection.class);

        registry.subscribe(channelId, c1);
        registry.subscribe(channelId, c2);

        assertThat(registry.connections(channelId)).containsExactlyInAnyOrder(c1, c2);
    }

    @Test
    void unknownChannelReturnsEmptySet() {
        assertThat(registry.connections(UUID.randomUUID())).isEmpty();
    }
}
