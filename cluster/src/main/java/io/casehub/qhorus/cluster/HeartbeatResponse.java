package io.casehub.qhorus.cluster;

import java.time.Instant;

public record HeartbeatResponse(String nodeId, Instant timestamp, String ringHash, String status) {}
