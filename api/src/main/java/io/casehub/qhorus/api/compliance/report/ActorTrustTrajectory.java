package io.casehub.qhorus.api.compliance.report;

import java.util.List;

public record ActorTrustTrajectory(
        String actorId,
        Double currentScore,
        List<TrustSnapshot> trajectory,
        List<AttestationSummaryEntry> attestations) {
}
