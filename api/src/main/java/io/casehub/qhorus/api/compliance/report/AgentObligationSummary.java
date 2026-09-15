package io.casehub.qhorus.api.compliance.report;

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
