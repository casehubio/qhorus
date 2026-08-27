package io.casehub.qhorus.compliance.graphql.dto;

import io.casehub.qhorus.compliance.model.AttributionReport;
import org.eclipse.microprofile.graphql.Type;

import java.time.Instant;
import java.util.List;

@Type("AttributionReport")
public record AttributionReportType(
        String correlationId,
        String rootEntryId,
        int channelCount,
        List<String> channels,
        Long totalDurationMs,
        String outcome,
        List<AttributionNodeType> nodes,
        List<AttributionEdgeType> edges,
        String merkleRoot,
        Instant generatedAt,
        int schemaVersion) {

    public static AttributionReportType from(AttributionReport r) {
        return new AttributionReportType(
                r.correlationId(), r.rootEntryId(), r.channelCount(), r.channels(),
                r.totalDurationMs(), r.outcome(),
                r.nodes().stream().map(AttributionNodeType::from).toList(),
                r.edges().stream().map(AttributionEdgeType::from).toList(),
                r.merkleRoot(), r.generatedAt(), r.schemaVersion());
    }
}
