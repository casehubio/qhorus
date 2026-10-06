package io.casehub.qhorus.cluster;

public record NodeInfo(String nodeId, String address) {
    public NodeInfo {
        if (nodeId == null || nodeId.isBlank()) {
            throw new IllegalArgumentException("nodeId must not be blank");
        }
        if (address == null || address.isBlank()) {
            throw new IllegalArgumentException("address must not be blank");
        }
    }
}
