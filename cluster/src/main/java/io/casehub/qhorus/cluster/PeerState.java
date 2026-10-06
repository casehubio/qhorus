package io.casehub.qhorus.cluster;

import java.time.Instant;

public record PeerState(NodeInfo nodeInfo, NodeState state, Instant lastHeartbeat, int missCount) {}
