package io.casehub.qhorus.api.channel;

import io.casehub.qhorus.api.message.Reaction;

public interface ReactionManager {

    Reaction react(Long messageId, String emoji);

    boolean unreact(Long messageId, String emoji);
}
