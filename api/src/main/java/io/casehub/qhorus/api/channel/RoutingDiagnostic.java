package io.casehub.qhorus.api.channel;

import java.util.List;

public record RoutingDiagnostic(
        List<RoutingCandidate> candidates,
        String selectedAgentId,
        double selectedTrustScore,
        String selectionOutcome,
        String reason,
        double effectiveThreshold,
        boolean routingAvailable) {}
