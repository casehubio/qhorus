package io.casehub.qhorus.compliance.model;

public record AttributionNode(
        String entryId,
        String channelId,
        String channelName,
        String messageType,
        String actorId,
        String occurredAt,
        String content,
        String causedByEntryId,
        int depth,
        Double trustScoreAtTime,
        String attestationVerdict,
        String algorithmRef,
        Double confidenceScore,
        String rationale) {
}
