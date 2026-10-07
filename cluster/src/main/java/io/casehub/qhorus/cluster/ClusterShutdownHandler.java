package io.casehub.qhorus.cluster;

import io.quarkus.arc.properties.IfBuildProperty;
import io.quarkus.runtime.ShutdownEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

@ApplicationScoped
@IfBuildProperty(name = "casehub.qhorus.relay.enabled", stringValue = "true",
                 enableIfMissing = false)
public class ClusterShutdownHandler {

    private static final Logger LOG = Logger.getLogger(ClusterShutdownHandler.class);

    @Inject
    ClusterManager clusterManager;

    @Inject
    WriteProxyClient proxyClient;

    void onShutdown(@Observes ShutdownEvent event) {
        LOG.info("Sending leave notifications to peers");
        clusterManager.shutdown(nodeInfo -> {
            try {
                proxyClient.sendLeave(nodeInfo, clusterManager.nodeId());
            } catch (Exception e) {
                LOG.debugf("Leave notification to %s failed: %s", nodeInfo.nodeId(), e.getMessage());
            }
        });
    }
}
