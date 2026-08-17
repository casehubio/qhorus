package io.casehub.qhorus.graphql.dto;

import io.casehub.qhorus.api.message.Commitment;
import io.casehub.qhorus.api.message.CommitmentState;
import java.time.Instant;
import java.util.UUID;
import org.eclipse.microprofile.graphql.Type;

@Type("Commitment")
public record CommitmentType(
        UUID id,
        String correlationId,
        UUID channelId,
        String messageType,
        String requester,
        String obligor,
        CommitmentState state,
        Instant expiresAt,
        Instant acknowledgedAt,
        Instant resolvedAt,
        String delegatedTo,
        UUID parentCommitmentId,
        Instant createdAt) {

    public static CommitmentType from(Commitment commitment) {
        return new CommitmentType(
                commitment.id(),
                commitment.correlationId(),
                commitment.channelId(),
                commitment.messageType() != null ? commitment.messageType().name() : null,
                commitment.requester(),
                commitment.obligor(),
                commitment.state(),
                commitment.expiresAt(),
                commitment.acknowledgedAt(),
                commitment.resolvedAt(),
                commitment.delegatedTo(),
                commitment.parentCommitmentId(),
                commitment.createdAt());
    }
}
