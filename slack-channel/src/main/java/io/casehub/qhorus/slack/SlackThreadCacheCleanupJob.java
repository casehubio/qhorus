package io.casehub.qhorus.slack;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;

import jakarta.enterprise.context.ApplicationScoped;

import org.jboss.logging.Logger;

import io.quarkus.scheduler.Scheduled;

/**
 * TTL eviction for abandoned thread cache entries.
 *
 * <p>Handles commitments that expired without a terminal message reaching the backend,
 * leaving orphaned entries in {@code slack_thread_cache}. Entries older than 30 days
 * are removed from both the DB and the in-memory cache.
 */
@ApplicationScoped
public class SlackThreadCacheCleanupJob {

    private static final Logger LOG = Logger.getLogger(SlackThreadCacheCleanupJob.class);
    private static final long TTL_DAYS = 30;

    private final SlackThreadCacheStore store;
    private final SlackChannelBackend backend;

    public SlackThreadCacheCleanupJob(SlackThreadCacheStore store, SlackChannelBackend backend) {
        this.store = store;
        this.backend = backend;
    }

    @Scheduled(every = "24h")
    public void evictStaleEntries() {
        Instant threshold = Instant.now().minus(TTL_DAYS, ChronoUnit.DAYS);
        int deleted = store.deleteOlderThan(threshold);
        if (deleted > 0) {
            evictFromMemoryCache(threshold);
            LOG.infof("Slack thread cache TTL eviction: removed %d stale entries (threshold=%s)", deleted, threshold);
        }
    }

    private void evictFromMemoryCache(Instant threshold) {
        // Find all entries older than threshold — walk the in-memory cache
        // Since createdAt isn't in memory, we use the DB delete result as the signal
        // and do a full sweep of any corrId whose threadTs is no longer in the DB
        for (var channelEntry : backend.threadCache.entrySet()) {
            UUID channelId = channelEntry.getKey();
            Map<UUID, String> channelThreads = channelEntry.getValue();
            channelThreads.keySet().removeIf(corrId ->
                    store.findThreadTs(channelId, corrId).isEmpty());
        }
    }
}
