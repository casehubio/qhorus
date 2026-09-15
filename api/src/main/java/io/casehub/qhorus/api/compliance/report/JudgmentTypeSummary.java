package io.casehub.qhorus.api.compliance.report;

public record JudgmentTypeSummary(
    String judgmentType,
    int total, int accepted, int rejected, int escalated, int pending,
    double acceptanceRate, double averageResponseTimeMs, double averageEvidenceQuality
) {}
