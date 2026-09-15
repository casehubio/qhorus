package io.casehub.qhorus.api.compliance.report;

import java.time.Instant;
import java.util.List;

public record JudgmentFulfillmentReport(
    Instant from, Instant to,
    List<JudgmentTypeSummary> byType,
    List<CallerSummary> byCaller,
    int totalJudgments, int accepted, int rejected, int escalated, int pending,
    double overallAcceptanceRate, double averageResponseTimeMs, double averageEvidenceQuality,
    String merkleRoot, Instant generatedAt, int schemaVersion
) {}
