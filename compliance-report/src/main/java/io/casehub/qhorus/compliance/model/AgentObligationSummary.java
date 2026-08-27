package io.casehub.qhorus.compliance.model;

public record AgentObligationSummary(
        String actorId,
        int total,
        int fulfilled,
        int failed,
        int declined,
        int delegated,
        int stillOpen,
        int stalled,
        double fulfillmentRate,
        Double currentTrustScore) {
}
