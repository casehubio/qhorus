package io.casehub.qhorus.cluster;

import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
@IfBuildProperty(name = "casehub.qhorus.relay.enabled", stringValue = "true",
                 enableIfMissing = false)
public class HeartbeatScheduler {

    @Inject
    HeartbeatService heartbeatService;

    @Scheduled(every = "${casehub.qhorus.relay.heartbeat-interval:3s}",
               concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void tick() {
        heartbeatService.tick();
    }
}
