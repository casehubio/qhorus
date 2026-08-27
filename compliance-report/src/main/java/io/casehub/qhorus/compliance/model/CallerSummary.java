package io.casehub.qhorus.compliance.model;

public record CallerSummary(
    String actorId,
    int total, int accepted, int rejected, int escalated, int pending,
    double acceptanceRate, double averageResponseTimeMs, double averageEvidenceQuality,
    Double currentTrustScore
) {}
