package io.casehub.qhorus.compliance.graphql.dto;

import io.casehub.qhorus.compliance.model.JudgmentAttributionReport;
import org.eclipse.microprofile.graphql.Type;

import java.time.Instant;
import java.util.List;

@Type("JudgmentAttributionReport")
public record JudgmentAttributionReportType(
        String judgmentId, String judgmentType,
        int channelCount, List<String> channels,
        String correlationId, String verificationOutcome,
        Long totalDurationMs,
        List<JudgmentEventType> events,
        List<AttributionNodeType> causalNodes,
        List<AttributionEdgeType> causalEdges,
        String merkleRoot, Instant generatedAt, int schemaVersion) {

    public static JudgmentAttributionReportType from(JudgmentAttributionReport r) {
        return new JudgmentAttributionReportType(
                r.judgmentId(), r.judgmentType(),
                r.channelCount(), r.channels(),
                r.correlationId(), r.verificationOutcome(),
                r.totalDurationMs(),
                r.events().stream().map(JudgmentEventType::from).toList(),
                r.causalNodes().stream().map(AttributionNodeType::from).toList(),
                r.causalEdges().stream().map(AttributionEdgeType::from).toList(),
                r.merkleRoot(), r.generatedAt(), r.schemaVersion());
    }
}
