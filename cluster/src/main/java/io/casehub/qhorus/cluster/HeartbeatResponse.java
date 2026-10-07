package io.casehub.qhorus.cluster;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record HeartbeatResponse(String nodeId, Instant timestamp, String ringHash, String status,
                                Map<UUID, OwnershipClaim> ownershipClaims) {
    public HeartbeatResponse(String nodeId, Instant timestamp, String ringHash, String status) {
        this(nodeId, timestamp, ringHash, status, Map.of());
    }
}
