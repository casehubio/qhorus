package io.casehub.qhorus.websocket.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.qhorus.api.gateway.MessageObserver;
import io.casehub.qhorus.api.gateway.MessageReceivedEvent;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

public class SpringWebSocketMessageObserver implements MessageObserver {

    private static final Logger LOG = Logger.getLogger(SpringWebSocketMessageObserver.class.getName());

    private final ObjectMapper objectMapper;
    private final SpringWebSocketConnectionRegistry registry;

    public SpringWebSocketMessageObserver(ObjectMapper objectMapper,
                                           SpringWebSocketConnectionRegistry registry) {
        this.objectMapper = objectMapper;
        this.registry = registry;
    }

    @Override
    public void onMessage(MessageReceivedEvent event) {
        Set<WebSocketSession> sessions = registry.connections(event.channelId());
        if (sessions.isEmpty()) {
            return;
        }

        String json;
        try {
            json = objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to serialize event for WebSocket push — channel={0}: {1}",
                    new Object[]{event.channelId(), e.getMessage()});
            return;
        }

        for (WebSocketSession session : Set.copyOf(sessions)) {
            if (registry.tryBufferForCatchUp(session, event.messageId(), json)) {
                continue;
            }
            try {
                session.sendMessage(new TextMessage(json));
            } catch (Exception e) {
                LOG.log(Level.FINE, "WebSocket send failed for channel {0}: {1}",
                        new Object[]{event.channelId(), e.getMessage()});
            }
        }
    }

    @Override
    public Scope scope() {
        return Scope.CLUSTER;
    }
}
