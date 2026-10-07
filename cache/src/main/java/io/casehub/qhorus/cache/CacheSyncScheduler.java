package io.casehub.qhorus.cache;

import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
@IfBuildProperty(name = "casehub.qhorus.cache.enabled", stringValue = "true",
                 enableIfMissing = true)
public class CacheSyncScheduler {

    @Inject
    FullSyncService fullSyncService;

    @Inject
    CacheConfig config;

    @Scheduled(every = "${casehub.qhorus.cache.full-sync-interval:5s}",
               concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void sync() {
        if ("full".equals(config.mode())) {
            fullSyncService.syncBatch();
        }
    }
}
