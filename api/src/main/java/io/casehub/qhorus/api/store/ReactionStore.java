package io.casehub.qhorus.api.store;

import io.casehub.qhorus.api.message.Reaction;

import java.util.UUID;

public interface ReactionStore extends ReactionReader {
    Reaction react(Long messageId, String emoji, String actorId, String tenancyId);

    boolean unreact(Long messageId, String emoji, String actorId);

    void deleteByMessage(Long messageId);

    void deleteByChannel(UUID channelId);
}
