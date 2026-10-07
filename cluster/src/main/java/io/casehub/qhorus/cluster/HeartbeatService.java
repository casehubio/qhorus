package io.casehub.qhorus.cluster;

import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.function.Function;

public class HeartbeatService {

    private static final Logger LOG = Logger.getLogger(HeartbeatService.class);
    static final         int    DEAD_PROBE_INTERVAL = 5;


    private final ClusterManager clusterManager;
    private final Function<NodeInfo, HeartbeatResponse> heartbeatCaller;
    private       int                                   tickCount = 0;


    public HeartbeatService(ClusterManager clusterManager,
                            Function<NodeInfo, HeartbeatResponse> heartbeatCaller) {
        this.clusterManager = clusterManager;
        this.heartbeatCaller = heartbeatCaller;
    }

    public void tick() {
        tickCount++;
        String  localRingHash     = clusterManager.ringHash();
        boolean probeDeadThisTick = tickCount % DEAD_PROBE_INTERVAL == 0;
        for (var entry : clusterManager.peerStates().entrySet()) {
            PeerState ps = entry.getValue();
            if (ps.state() == NodeState.DEAD && !probeDeadThisTick) {
                continue;
            }
            try {
                HeartbeatResponse resp = heartbeatCaller.apply(ps.nodeInfo());
                clusterManager.recordHeartbeat(entry.getKey());
                if (resp != null) {
                    if (resp.ringHash() != null && !resp.ringHash().equals(localRingHash)) {
                        LOG.warnf("Ring disagreement with %s — local=%s remote=%s",
                                  entry.getKey(), localRingHash, resp.ringHash());
                    }
                    if (resp.ownershipClaims() != null && !resp.ownershipClaims().isEmpty()) {
                        clusterManager.updateRemoteOwnership(entry.getKey(), resp.ownershipClaims());
                    }
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
                "UP",
                clusterManager.getLocalClaims());
    }
}
