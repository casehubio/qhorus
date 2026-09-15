package io.casehub.qhorus.api.message;

import java.util.List;

public record MessageReactions(
    Long messageId,
    List<ReactionGroup> reactions
) {}
