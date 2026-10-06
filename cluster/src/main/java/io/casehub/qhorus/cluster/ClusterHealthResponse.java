package io.casehub.qhorus.cluster;

import java.util.Map;

public record ClusterHealthResponse(
        String nodeId, String status, int clusterSize, int expectedSize,
        String ringHash, Map<String, PeerState> peers) {}
