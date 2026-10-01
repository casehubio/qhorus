package io.casehub.qhorus.a2a.push.core;

import io.casehub.qhorus.api.a2a.PushNotificationConfig;
import io.casehub.qhorus.api.store.CrossTenantPushNotificationConfigStore;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class PushNotificationCleanupJobCore {

    private static final Logger LOG = Logger.getLogger(PushNotificationCleanupJobCore.class.getName());

    private final CrossTenantPushNotificationConfigStore store;
    private final Duration ttlThreshold;
    private final boolean enabled;
    private final Clock clock;

    public PushNotificationCleanupJobCore(CrossTenantPushNotificationConfigStore store,
                                           Duration ttlThreshold, boolean enabled, Clock clock) {
        this.store = store;
        this.ttlThreshold = ttlThreshold;
        this.enabled = enabled;
        this.clock = clock;
    }

    public void cleanup() {
        if (!enabled) {
            return;
        }
        Instant threshold = clock.instant().minus(ttlThreshold);
        List<PushNotificationConfig> expired = store.findExpired(threshold);
        if (expired.isEmpty()) {
            return;
        }
        for (PushNotificationConfig cfg : expired) {
            try {
                store.delete(cfg.id());
                LOG.log(Level.FINE, "TTL cleanup: deleted push config {0} for task {1} (url={2})",
                        new Object[]{cfg.id(), cfg.taskId(), cfg.url()});
            } catch (Exception e) {
                LOG.log(Level.WARNING, "TTL cleanup: failed to delete push config {0}: {1}",
                        new Object[]{cfg.id(), e.getMessage()});
            }
        }
        LOG.log(Level.INFO, "TTL cleanup: removed {0} expired push config(s)", expired.size());
    }
}
