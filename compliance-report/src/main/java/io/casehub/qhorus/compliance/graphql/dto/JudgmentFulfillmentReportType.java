package io.casehub.qhorus.compliance.graphql.dto;

import io.casehub.qhorus.compliance.model.JudgmentFulfillmentReport;
import org.eclipse.microprofile.graphql.Type;

import java.time.Instant;
import java.util.List;

@Type("JudgmentFulfillmentReport")
public record JudgmentFulfillmentReportType(
        Instant from, Instant to,
        List<JudgmentTypeSummaryType> byType,
        List<CallerSummaryType> byCaller,
        int totalJudgments, int accepted, int rejected, int escalated, int pending,
        double overallAcceptanceRate, double averageResponseTimeMs, double averageEvidenceQuality,
        String merkleRoot, Instant generatedAt, int schemaVersion) {

    public static JudgmentFulfillmentReportType from(JudgmentFulfillmentReport r) {
        return new JudgmentFulfillmentReportType(
                r.from(), r.to(),
                r.byType().stream().map(JudgmentTypeSummaryType::from).toList(),
                r.byCaller().stream().map(CallerSummaryType::from).toList(),
                r.totalJudgments(), r.accepted(), r.rejected(), r.escalated(), r.pending(),
                r.overallAcceptanceRate(), r.averageResponseTimeMs(), r.averageEvidenceQuality(),
                r.merkleRoot(), r.generatedAt(), r.schemaVersion());
    }
}
