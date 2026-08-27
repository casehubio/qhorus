package io.casehub.qhorus.compliance.graphql.dto;

import io.casehub.qhorus.compliance.model.AttributionNode;
import org.eclipse.microprofile.graphql.Type;

@Type("AttributionNode")
public record AttributionNodeType(
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

    public static AttributionNodeType from(AttributionNode n) {
        return new AttributionNodeType(
                n.entryId(), n.channelId(), n.channelName(), n.messageType(),
                n.actorId(), n.occurredAt(), n.content(), n.causedByEntryId(),
                n.depth(), n.trustScoreAtTime(), n.attestationVerdict(),
                n.algorithmRef(), n.confidenceScore(), n.rationale());
    }
}
