package io.casehub.qhorus.cluster;

import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;

import java.net.InetAddress;
import java.time.Clock;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
@IfBuildProperty(name = "casehub.qhorus.relay.enabled", stringValue = "true", enableIfMissing = false)
public class RelayProducer {

    @Inject
    RelayConfig config;

    @Inject
    OwnershipConfig ownershipConfig;

    @Produces
    @ApplicationScoped
    public ClusterManager clusterManager(WriteFrequencyTracker tracker) {
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

        ClusterManager manager = new ClusterManager(nodeId, peerMap, config.virtualNodes(),
                                                    config.heartbeatMissThreshold(), config.quorumEnforced(),
                                                    Clock.systemUTC());

        if ("dynamic".equals(config.routing())) {
            ConsistentHashRing       ring     = new ConsistentHashRing(peerMap.keySet(), config.virtualNodes());
            DynamicOwnershipResolver resolver = new DynamicOwnershipResolver(ring, peerMap);
            manager.setResolver(resolver);

            OwnershipEvaluator evaluator = new OwnershipEvaluator(nodeId, tracker, resolver,
                                                                  ownershipConfig.hysteresisRatio(), ownershipConfig.minClaimWrites());
            manager.setEvaluator(evaluator);
        }

        return manager;
    }

    @Produces
    @ApplicationScoped
    public WriteProxyClient writeProxyClient() {
        return new WriteProxyClient(config.proxyTimeout(), config.internalSecret().orElse(null));
    }

    @Produces
    @ApplicationScoped
    public HeartbeatService heartbeatService(ClusterManager clusterManager,
                                             WriteProxyClient proxyClient) {
        return new HeartbeatService(clusterManager, proxyClient::heartbeat);
    }


    @Produces
    @ApplicationScoped
    @jakarta.annotation.Priority(100)
    @jakarta.enterprise.inject.Alternative
    public io.casehub.qhorus.api.message.MessageDispatcher messageDispatcher(
            io.casehub.qhorus.runtime.cdi.CdiMessageService delegate,
            ClusterManager clusterManager,
            WriteProxyClient proxyClient,
            WriteFrequencyTracker tracker) {
        boolean routing = !"none".equals(config.routing());
        return new WriteRoutingDecorator(delegate, clusterManager, proxyClient, routing, tracker);
    }

    @Produces
    @ApplicationScoped
    @jakarta.annotation.Priority(100)
    @jakarta.enterprise.inject.Alternative
    public io.casehub.qhorus.api.channel.ChannelManager channelManager(
            io.casehub.qhorus.runtime.channel.ChannelService delegate,
            ClusterManager clusterManager,
            WriteProxyClient proxyClient) {
        boolean routing = !"none".equals(config.routing());
        return new ChannelManagerDecorator(delegate, clusterManager, proxyClient, routing);
    }


    @Produces
    @ApplicationScoped
    public WriteFrequencyTracker writeFrequencyTracker() {
        int      bucketCount    = ownershipConfig.bucketCount();
        int      windowSeconds  = ownershipConfig.windowSeconds();
        Duration bucketDuration = Duration.ofSeconds(windowSeconds / bucketCount);
        return new WriteFrequencyTracker(bucketCount, bucketDuration, Clock.systemUTC());
    }
}
