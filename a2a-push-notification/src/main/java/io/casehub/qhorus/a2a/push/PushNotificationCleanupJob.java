package io.casehub.qhorus.a2a.push;

import io.casehub.qhorus.a2a.push.core.PushNotificationCleanupJobCore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.quarkus.scheduler.Scheduled;

@ApplicationScoped
public class PushNotificationCleanupJob {

    @Inject
    PushNotificationCleanupJobCore core;

    @Scheduled(every = "${casehub.qhorus.a2a.push.cleanup-interval:5m}")
    void cleanup() {
        core.cleanup();
    }
}
