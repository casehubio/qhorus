package io.casehub.qhorus.compliance.graphql.dto;

import io.casehub.qhorus.compliance.model.JudgmentTypeSummary;
import org.eclipse.microprofile.graphql.Type;

@Type("JudgmentTypeSummary")
public record JudgmentTypeSummaryType(
        String judgmentType,
        int total, int accepted, int rejected, int escalated, int pending,
        double acceptanceRate, double averageResponseTimeMs, double averageEvidenceQuality) {

    public static JudgmentTypeSummaryType from(JudgmentTypeSummary s) {
        return new JudgmentTypeSummaryType(s.judgmentType(),
                s.total(), s.accepted(), s.rejected(), s.escalated(), s.pending(),
                s.acceptanceRate(), s.averageResponseTimeMs(), s.averageEvidenceQuality());
    }
}
