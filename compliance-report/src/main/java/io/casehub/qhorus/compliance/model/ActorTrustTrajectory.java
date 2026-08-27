package io.casehub.qhorus.compliance.model;

import java.util.List;

public record ActorTrustTrajectory(
        String actorId,
        Double currentScore,
        List<TrustSnapshot> trajectory,
        List<AttestationSummaryEntry> attestations) {
}
