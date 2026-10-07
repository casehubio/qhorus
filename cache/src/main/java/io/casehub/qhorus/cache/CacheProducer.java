package io.casehub.qhorus.cache;

import io.casehub.qhorus.api.store.ChannelStore;
import io.casehub.qhorus.runtime.store.jpa.JpaMessageStore;
import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

@ApplicationScoped
@IfBuildProperty(name = "casehub.qhorus.cache.enabled", stringValue = "true", enableIfMissing = false)
public class CacheProducer {

    @Inject
    CacheConfig config;

    @Produces
    @ApplicationScoped
    @Alternative
    @Priority(1)
    public CachingMessageStore cachingMessageStore(JpaMessageStore delegate) {
        boolean fullMode = "full".equals(config.mode());
        return new CachingMessageStore(delegate, config.maxChannels(),
                                       config.maxMessagesPerChannel(), fullMode);
    }

    @Produces
    @ApplicationScoped
    public FullSyncService fullSyncService(JpaMessageStore jpaStore, ChannelStore channelStore,
                                           CachingMessageStore cachingStore) {
        boolean fullMode = "full".equals(config.mode());
        return new FullSyncService(jpaStore, channelStore, cachingStore,
                                   config.fullSyncBatchSize(), fullMode);
    }
}