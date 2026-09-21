package io.casehub.qhorus.api.channel;

public record RoutingCandidate(
        String agentId,
        String name,
        double trustScore,
        boolean passesThreshold) {}
