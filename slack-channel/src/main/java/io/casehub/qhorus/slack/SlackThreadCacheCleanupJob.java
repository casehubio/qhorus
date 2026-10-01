package io.casehub.qhorus.slack;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import jakarta.enterprise.context.ApplicationScoped;

import io.casehub.qhorus.slack.core.SlackChannelBackendCore;
import io.quarkus.scheduler.Scheduled;

@ApplicationScoped
public class SlackThreadCacheCleanupJob {

    private static final long TTL_DAYS = 30;

    private final SlackChannelBackendCore backendCore;

    public SlackThreadCacheCleanupJob(SlackChannelBackendCore backendCore) {
        this.backendCore = backendCore;
    }

    @Scheduled(every = "24h")
    public void evictStaleEntries() {
        Instant threshold = Instant.now().minus(TTL_DAYS, ChronoUnit.DAYS);
        backendCore.evictStaleThreadCacheEntries(threshold);
    }
}
