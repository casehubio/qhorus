package io.casehub.qhorus.runtime.gateway;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.casehub.qhorus.api.gateway.MessageObserver;
import io.casehub.qhorus.api.gateway.MessageReceivedEvent;
import io.quarkus.arc.DefaultBean;

/**
 * Default {@link MessageObserver} — fires a CDI event for in-JVM observers.
 * Active in all embedded harnesses. Additional CLUSTER-scoped implementations
 * coexist alongside it via {@code Instance<MessageObserver>} iteration.
 */
@DefaultBean
@ApplicationScoped
public class InProcessMessageBus implements MessageObserver {

    private static final Logger LOG = Logger.getLogger(InProcessMessageBus.class);

    @Inject
    Event<MessageReceivedEvent> cdiEvent;

    @Override
    public void onMessage(final MessageReceivedEvent event) {
        cdiEvent.fireAsync(event)
                .exceptionally(t -> {
                    LOG.warnf("CDI observer failed for channel '%s' type %s: %s",
                            event.channelName(), event.messageType(), t.getMessage());
                    return null;
                });
    }
}
