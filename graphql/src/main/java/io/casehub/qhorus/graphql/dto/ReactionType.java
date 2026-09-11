package io.casehub.qhorus.graphql.dto;

import io.casehub.qhorus.api.message.Reaction;
import java.time.Instant;

@org.eclipse.microprofile.graphql.Type("Reaction")
public record ReactionType(
        Long id,
        Long messageId,
        String emoji,
        String actorId,
        Instant createdAt) {

    public static ReactionType from(Reaction r) {
        return new ReactionType(r.id(), r.messageId(), r.emoji(), r.actorId(), r.createdAt());
    }
}
