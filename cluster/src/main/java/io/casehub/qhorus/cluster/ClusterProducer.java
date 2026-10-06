package io.casehub.qhorus.cluster;

import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import java.net.InetAddress;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
@IfBuildProperty(name = "casehub.qhorus.cluster.enabled", stringValue = "true", enableIfMissing = false)
public class ClusterProducer {

    @Inject
    ClusterConfig config;

    @Produces
    @ApplicationScoped
    public ClusterManager clusterManager() {
        String nodeId = config.nodeId().orElseGet(() -> {
            try {
                return InetAddress.getLocalHost().getHostName();
            } catch (Exception e) {
                return "unknown";
            }
        });

        List<String> peerList = config.peers().orElse(List.of());
        if (peerList.isEmpty()) {
            peerList = List.of(nodeId + ":8080");
        }

        Map<String, String> peerMap = new LinkedHashMap<>();
        for (String peer : peerList) {
            String[] parts = peer.split(":", 2);
            if (parts.length != 2) {
                throw new IllegalStateException("Malformed peer address: " + peer + " — expected host:port");
            }
            String id = parts[0];
            if (peerMap.containsKey(id)) {
                throw new IllegalStateException("Duplicate node ID in peer list: " + id);
            }
            peerMap.put(id, peer);
        }

        return new ClusterManager(nodeId, peerMap, config.virtualNodes(),
                config.heartbeatMissThreshold(), config.quorumEnforced(),
                Clock.systemUTC());
    }

    @Produces
    @ApplicationScoped
    public WriteProxyClient writeProxyClient() {
        return new WriteProxyClient();
    }
}
