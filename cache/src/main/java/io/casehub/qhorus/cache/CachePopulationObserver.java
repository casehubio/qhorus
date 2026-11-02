package io.casehub.qhorus.cache;

import io.casehub.qhorus.api.gateway.MessageObserver;
import io.casehub.qhorus.api.gateway.MessageReceivedEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

@ApplicationScoped
public class CachePopulationObserver implements MessageObserver {

    @Inject
    Instance<CachingMessageStore> cachingStore;

    @Override
    public void onMessage(MessageReceivedEvent event) {
        if (!cachingStore.isResolvable() || event.messageId() == null || event.channelId() == null) {
            return;
        }
        // The event omits message metadata, so it cannot replace a persisted cache entry.
        cachingStore.get().invalidateChannel(event.channelId());
    }

    @Override
    public Scope scope() {
        return Scope.CLUSTER;
    }
}