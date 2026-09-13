package io.casehub.qhorus.runtime.gateway;

import org.jboss.logging.Logger;

import io.casehub.qhorus.api.gateway.MessageObserver;
import io.casehub.qhorus.api.gateway.MessageReceivedEvent;

import java.util.function.Consumer;

public class InProcessMessageBus implements MessageObserver {

    private static final Logger LOG = Logger.getLogger(InProcessMessageBus.class);

    private final Consumer<MessageReceivedEvent> eventConsumer;

    public InProcessMessageBus(Consumer<MessageReceivedEvent> eventConsumer) {
        this.eventConsumer = eventConsumer;
    }

    @Override
    public void onMessage(final MessageReceivedEvent event) {
        try {
            eventConsumer.accept(event);
        } catch (Exception e) {
            LOG.warnf("Observer failed for channel '%s' type %s: %s",
                    event.channelName(), event.messageType(), e.getMessage());
        }
    }
}
