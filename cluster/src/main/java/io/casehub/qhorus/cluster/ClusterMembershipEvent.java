package io.casehub.qhorus.cluster;

import java.time.Instant;

public record ClusterMembershipEvent(
        String nodeId,
        NodeState previousState,
        NodeState newState,
        Instant timestamp) {}
