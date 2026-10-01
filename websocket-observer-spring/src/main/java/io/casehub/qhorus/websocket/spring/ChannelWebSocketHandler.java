package io.casehub.qhorus.websocket.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.qhorus.api.channel.Channel;
import io.casehub.qhorus.api.gateway.MessageReceivedEvent;
import io.casehub.qhorus.api.message.Message;
import io.casehub.qhorus.api.store.CrossTenantChannelStore;
import io.casehub.qhorus.api.store.CrossTenantMessageStore;
import io.casehub.qhorus.api.store.query.MessageQuery;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ChannelWebSocketHandler extends TextWebSocketHandler {

    private static final Logger LOG = Logger.getLogger(ChannelWebSocketHandler.class.getName());

    private final SpringWebSocketConnectionRegistry registry;
    private final CrossTenantChannelStore channelStore;
    private final CrossTenantMessageStore messageStore;
    private final ObjectMapper objectMapper;
    private final int maxMessages;

    public ChannelWebSocketHandler(SpringWebSocketConnectionRegistry registry,
                                    CrossTenantChannelStore channelStore,
                                    CrossTenantMessageStore messageStore,
                                    ObjectMapper objectMapper,
                                    int maxMessages) {
        this.registry = registry;
        this.channelStore = channelStore;
        this.messageStore = messageStore;
        this.objectMapper = objectMapper;
        this.maxMessages = maxMessages;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String channelId = extractChannelId(session);
        if (channelId == null) {
            session.close(new CloseStatus(1008, "Missing channelId"));
            return;
        }

        UUID id;
        try {
            id = UUID.fromString(channelId);
        } catch (IllegalArgumentException e) {
            LOG.log(Level.WARNING, "Invalid channelId: {0}", channelId);
            session.close(new CloseStatus(1008, "Invalid channelId"));
            return;
        }

        Optional<Channel> channelOpt = channelStore.findById(id);
        if (channelOpt.isEmpty()) {
            LOG.log(Level.FINE, "Unknown channel: {0}", channelId);
            session.close(new CloseStatus(1008, "Unknown channel"));
            return;
        }

        String channelName = channelOpt.get().name();
        Long lastEventId = parseLastEventId(session);

        if (lastEventId == null) {
            registry.subscribe(id, session);
            LOG.log(Level.FINE, "WebSocket client subscribed to channel {0} (live-only)", channelId);
            return;
        }

        registry.subscribeCatchingUp(id, session);
        LOG.log(Level.FINE, "WebSocket client subscribed to channel {0} with catch-up from {1}",
                new Object[]{channelId, lastEventId});

        try {
            sendControl(session, Map.of("control", "catchup_begin"));

            List<Message> messages = messageStore.scan(
                    MessageQuery.poll(id, lastEventId, maxMessages + 1));

            boolean truncated = messages.size() > maxMessages;
            List<Message> toSend = truncated ? messages.subList(0, maxMessages) : messages;

            long highestSentMessageId = lastEventId;
            for (Message msg : toSend) {
                MessageReceivedEvent event = MessageReceivedEvent.fromMessage(msg, channelName);
                session.sendMessage(new TextMessage(objectMapper.writeValueAsString(event)));
                if (msg.id() != null && msg.id() > highestSentMessageId) {
                    highestSentMessageId = msg.id();
                }
            }

            List<SpringWebSocketConnectionRegistry.BufferedMessage> buffered =
                    registry.completeCatchUp(id, session);
            for (var buf : buffered) {
                if (buf.messageId() != null && buf.messageId() > highestSentMessageId) {
                    session.sendMessage(new TextMessage(buf.json()));
                    highestSentMessageId = buf.messageId();
                }
            }

            if (truncated) {
                long headId = messageStore.findLastMessage(id)
                        .map(Message::id).orElse(highestSentMessageId);
                sendControl(session, Map.of(
                        "control", "catchup_truncated",
                        "oldestAvailableId", toSend.get(0).id(),
                        "headId", headId));
            } else {
                sendControl(session, Map.of(
                        "control", "catchup_end",
                        "lastMessageId", highestSentMessageId));
            }
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Catch-up failed for channel {0}: {1}",
                    new Object[]{channelId, e.getMessage()});
            registry.cancelCatchUp(id, session);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        String channelId = extractChannelId(session);
        if (channelId != null) {
            try {
                UUID id = UUID.fromString(channelId);
                registry.unsubscribe(id, session);
                LOG.log(Level.FINE, "WebSocket client unsubscribed from channel {0}", channelId);
            } catch (IllegalArgumentException e) {
                LOG.log(Level.FINE, "Invalid channelId on close: {0}", channelId);
            }
        }
    }

    private String extractChannelId(WebSocketSession session) {
        @SuppressWarnings("unchecked")
        Map<String, String> pathVars = (Map<String, String>) session.getAttributes()
                .get("org.springframework.web.servlet.HandlerMapping.uriTemplateVariables");
        if (pathVars != null) {
            return pathVars.get("channelId");
        }
        URI uri = session.getUri();
        if (uri != null) {
            String path = uri.getPath();
            int lastSlash = path.lastIndexOf('/');
            if (lastSlash >= 0 && lastSlash < path.length() - 1) {
                return path.substring(lastSlash + 1);
            }
        }
        return null;
    }

    private Long parseLastEventId(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null || uri.getQuery() == null) {
            return null;
        }
        try {
            return UriComponentsBuilder.fromUri(uri).build()
                    .getQueryParams()
                    .getFirst("lastEventId") != null
                    ? Long.parseLong(UriComponentsBuilder.fromUri(uri).build()
                    .getQueryParams().getFirst("lastEventId"))
                    : null;
        } catch (NumberFormatException e) {
            LOG.log(Level.WARNING, "Invalid lastEventId value in query: {0}", uri.getQuery());
            return null;
        }
    }

    private void sendControl(WebSocketSession session, Map<String, Object> control) {
        try {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(control)));
        } catch (Exception e) {
            LOG.log(Level.WARNING, "Failed to send control frame: {0}", e.getMessage());
        }
    }
}
