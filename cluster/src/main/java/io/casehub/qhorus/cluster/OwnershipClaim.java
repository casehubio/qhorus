package io.casehub.qhorus.cluster;

public record OwnershipClaim(String nodeId, long writeCount) {
    public OwnershipClaim {
        if (nodeId == null || nodeId.isBlank()) {
            throw new IllegalArgumentException("nodeId must not be blank");
        }
        if (writeCount < 0) {
            throw new IllegalArgumentException("writeCount must not be negative");
        }
    }
}
