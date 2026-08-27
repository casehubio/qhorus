package io.casehub.qhorus.compliance.graphql.dto;

import io.casehub.qhorus.compliance.model.CallerSummary;
import org.eclipse.microprofile.graphql.Type;

@Type("CallerSummary")
public record CallerSummaryType(
        String actorId,
        int total, int accepted, int rejected, int escalated, int pending,
        double acceptanceRate, double averageResponseTimeMs, double averageEvidenceQuality,
        Double currentTrustScore) {

    public static CallerSummaryType from(CallerSummary s) {
        return new CallerSummaryType(s.actorId(),
                s.total(), s.accepted(), s.rejected(), s.escalated(), s.pending(),
                s.acceptanceRate(), s.averageResponseTimeMs(), s.averageEvidenceQuality(),
                s.currentTrustScore());
    }
}
