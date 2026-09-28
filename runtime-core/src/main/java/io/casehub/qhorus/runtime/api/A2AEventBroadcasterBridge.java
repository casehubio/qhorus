package io.casehub.qhorus.runtime.api;

import io.casehub.qhorus.api.gateway.OutboundMessage;
import io.casehub.qhorus.api.message.MessageType;
import org.jboss.logging.Logger;

import java.util.Set;
import java.util.function.BiConsumer;

public class A2AEventBroadcasterBridge {

    private static final Logger LOG = Logger.getLogger(A2AEventBroadcasterBridge.class);

    private static final Set<MessageType> TERMINAL_TYPES = Set.of(
            MessageType.DONE, MessageType.FAILURE, MessageType.DECLINE, MessageType.RESPONSE);

    private static final String TOPIC_PREFIX = "a2a.task.";

    private final boolean enabled;
    private final BiConsumer<String, String> broadcaster;

    public A2AEventBroadcasterBridge(boolean enabled, BiConsumer<String, String> broadcaster) {
        this.enabled = enabled;
        this.broadcaster = broadcaster;
    }

    public void publish(OutboundMessage message) {
        if (!enabled || message.correlationId() == null) {
            return;
        }

        boolean terminal = TERMINAL_TYPES.contains(message.type());
        String state = A2ATaskStateMapper.fromMessageType(message.type());
        String topic = TOPIC_PREFIX + message.correlationId();

        String json = "{\"id\":\"%s\",\"status\":{\"state\":\"%s\"},\"type\":\"%s\",\"final\":%b}"
                .formatted(message.correlationId(), state, message.type().name(), terminal);

        try {
            broadcaster.accept(topic, json);
        } catch (Exception e) {
            LOG.debugf(e, "EventBroadcaster publish failed for task %s", message.correlationId());
        }
    }
}
