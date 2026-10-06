package io.casehub.qhorus.cluster;

import java.time.Instant;
import java.util.List;

public record TopologyResponse(List<NodeSummary> nodes) {
    public record NodeSummary(String nodeId, String address, NodeState status,
                               Instant lastHeartbeat) {}
}
