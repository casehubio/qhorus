package io.casehub.qhorus.cache;

import io.casehub.qhorus.api.gateway.MessageObserver;
import io.casehub.qhorus.api.gateway.MessageReceivedEvent;
import io.casehub.qhorus.api.message.Message;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class CachePopulationObserver implements MessageObserver {

    @Inject
    Instance<CachingMessageStore> cachingStore;

    @Override
    public void onMessage(MessageReceivedEvent event) {
        if (!cachingStore.isResolvable() || event.messageId() == null || event.channelId() == null) {
            return;
        }
        Message msg = new Message(
                event.messageId(), event.channelId(), event.senderId(),
                event.messageType(), event.actorType(), event.tenancyId(),
                event.content(), event.payload(), event.correlationId(),
                null, 0, List.of(), event.target(), event.topic(),
                null, null, null, 0, event.occurredAt(), null, false);
        cachingStore.get().addToBuffer(event.channelId(), msg);
    }

    @Override
    public Scope scope() {
        return Scope.CLUSTER;
    }
}