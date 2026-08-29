package io.casehub.qhorus.a2a.push;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import org.jboss.logging.Logger;

import io.casehub.qhorus.api.a2a.PushNotificationConfig;
import io.casehub.qhorus.api.store.CrossTenantPushNotificationConfigStore;
import io.quarkus.scheduler.Scheduled;

@ApplicationScoped
public class PushNotificationCleanupJob {

    private static final Logger LOG = Logger.getLogger(PushNotificationCleanupJob.class);

    private final CrossTenantPushNotificationConfigStore store;
    private final PushConfig config;
    Clock clock = Clock.systemUTC();

    @Inject
    public PushNotificationCleanupJob(CrossTenantPushNotificationConfigStore store,
                                      PushConfig config) {
        this.store = store;
        this.config = config;
    }

    PushNotificationCleanupJob(CrossTenantPushNotificationConfigStore store,
                               PushConfig config, Clock clock) {
        this.store = store;
        this.config = config;
        this.clock = clock;
    }

    @Scheduled(every = "${casehub.qhorus.a2a.push.cleanup-interval:5m}")
    void cleanup() {
        if (!config.enabled()) {
            return;
        }
        Instant threshold = clock.instant().minus(config.ttlThreshold());
        List<PushNotificationConfig> expired = store.findExpired(threshold);
        if (expired.isEmpty()) {
            return;
        }
        for (PushNotificationConfig cfg : expired) {
            try {
                store.delete(cfg.id());
                LOG.debugf("TTL cleanup: deleted push config %s for task %s (url=%s)",
                        cfg.id(), cfg.taskId(), cfg.url());
            } catch (Exception e) {
                LOG.warnf("TTL cleanup: failed to delete push config %s: %s",
                        cfg.id(), e.getMessage());
            }
        }
        LOG.infof("TTL cleanup: removed %d expired push config(s)", expired.size());
    }
}
