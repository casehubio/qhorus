package io.casehub.qhorus.cluster;

import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.function.Function;

public class HeartbeatService {

    private static final Logger LOG = Logger.getLogger(HeartbeatService.class);

    private final ClusterManager clusterManager;
    private final Function<NodeInfo, HeartbeatResponse> heartbeatCaller;

    public HeartbeatService(ClusterManager clusterManager,
                            Function<NodeInfo, HeartbeatResponse> heartbeatCaller) {
        this.clusterManager = clusterManager;
        this.heartbeatCaller = heartbeatCaller;
    }

    public void tick() {
        String localRingHash = clusterManager.ringHash();
        for (var entry : clusterManager.peerStates().entrySet()) {
            PeerState ps = entry.getValue();
            if (ps.state() == NodeState.DEAD) {
                continue;
            }
            try {
                HeartbeatResponse resp = heartbeatCaller.apply(ps.nodeInfo());
                clusterManager.recordHeartbeat(entry.getKey());
                if (resp != null && resp.ringHash() != null
                        && !resp.ringHash().equals(localRingHash)) {
                    LOG.warnf("Ring disagreement with %s — local=%s remote=%s",
                            entry.getKey(), localRingHash, resp.ringHash());
                }
            } catch (Exception e) {
                clusterManager.recordMiss(entry.getKey());
            }
        }
    }

    public HeartbeatResponse buildLocalResponse() {
        return new HeartbeatResponse(
                clusterManager.nodeId(),
                Instant.now(),
                clusterManager.ringHash(),
                "UP");
    }
}
