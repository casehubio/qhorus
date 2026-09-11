package io.casehub.qhorus.graphql.dto;

import java.util.List;

@org.eclipse.microprofile.graphql.Type("MessageReactions")
public record MessageReactionsType(
        Long messageId,
        List<ReactionGroupType> reactions) {
}
