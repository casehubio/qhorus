package io.casehub.qhorus.websocket;

import java.util.UUID;

import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.PathParam;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;

@WebSocket(path = "/qhorus/ws/channels/{channelId}")
public class ChannelWebSocketEndpoint {

    private static final Logger LOG = Logger.getLogger(ChannelWebSocketEndpoint.class);

    @Inject
    WebSocketConnectionRegistry registry;

    @OnOpen
    void onOpen(@PathParam String channelId, WebSocketConnection connection) {
        UUID id = UUID.fromString(channelId);
        registry.subscribe(id, connection);
        LOG.debugf("WebSocket client subscribed to channel %s", channelId);
    }

    @OnClose
    void onClose(@PathParam String channelId, WebSocketConnection connection) {
        UUID id = UUID.fromString(channelId);
        registry.unsubscribe(id, connection);
        LOG.debugf("WebSocket client unsubscribed from channel %s", channelId);
    }
}
