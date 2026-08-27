package io.casehub.qhorus.compliance.model;

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
