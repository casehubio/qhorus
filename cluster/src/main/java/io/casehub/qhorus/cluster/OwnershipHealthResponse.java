package io.casehub.qhorus.cluster;

import java.util.Map;
import java.util.UUID;

public record OwnershipHealthResponse(
        String nodeId,
        int localClaimCount,
        Map<UUID, OwnershipClaim> claims) {
}