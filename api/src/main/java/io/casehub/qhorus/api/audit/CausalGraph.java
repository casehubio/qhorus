package io.casehub.qhorus.api.audit;

import java.util.List;

public record CausalGraph(
        String correlationId,
        String rootEntryId,
        int channelCount,
        List<String> channels,
        Long totalDurationMs,
        String outcome,
        boolean truncated,
        List<GraphNode> nodes,
        List<GraphEdge> edges) {

    public record GraphNode(
            String entryId,
            String channelId,
            String channelName,
            String messageType,
            String actorId,
            String occurredAt,
            String content,
            String causedByEntryId,
            int depth) {
    }

    public record GraphEdge(
            String from,
            String to,
            String type,
            Long elapsedMs) {
    }
}
