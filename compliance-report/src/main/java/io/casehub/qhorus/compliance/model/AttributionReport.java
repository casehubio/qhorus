package io.casehub.qhorus.compliance.model;

import java.time.Instant;
import java.util.List;

public record AttributionReport(
        String correlationId,
        String rootEntryId,
        int channelCount,
        List<String> channels,
        Long totalDurationMs,
        String outcome,
        List<AttributionNode> nodes,
        List<AttributionEdge> edges,
        String merkleRoot,
        Instant generatedAt,
        int schemaVersion) {
}
