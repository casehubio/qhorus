package io.casehub.qhorus.cluster;

import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
@IfBuildProperty(name = "casehub.qhorus.relay.enabled", stringValue = "true", enableIfMissing = false)
public class OwnershipScheduler {

    @Inject
    ClusterManager clusterManager;

    @Inject
    RelayConfig config;

    @Scheduled(every = "${casehub.qhorus.relay.ownership.evaluation-interval-seconds:10}s",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void evaluateOwnership() {
        if (!"dynamic".equals(config.routing())) {
            return;
        }
        clusterManager.evaluateOwnership();
    }
}