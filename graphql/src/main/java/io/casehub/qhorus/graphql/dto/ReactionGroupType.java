package io.casehub.qhorus.graphql.dto;

import io.casehub.qhorus.api.message.ReactionGroup;
import java.util.List;

@org.eclipse.microprofile.graphql.Type("ReactionGroup")
public record ReactionGroupType(
        String emoji,
        int count,
        List<String> actorIds) {

    public static ReactionGroupType from(ReactionGroup rg) {
        return new ReactionGroupType(rg.emoji(), rg.count(), rg.actorIds());
    }
}
