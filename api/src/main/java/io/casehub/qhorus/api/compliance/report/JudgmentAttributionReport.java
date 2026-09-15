package io.casehub.qhorus.api.compliance.report;

import java.time.Instant;
import java.util.List;

public record JudgmentAttributionReport(
    String judgmentId,
    String judgmentType,
    int channelCount,
    List<String> channels,
    String correlationId,
    String verificationOutcome,
    Long totalDurationMs,
    List<JudgmentEvent> events,
    List<AttributionNode> causalNodes,
    List<AttributionEdge> causalEdges,
    String merkleRoot,
    Instant generatedAt,
    int schemaVersion
) {}
