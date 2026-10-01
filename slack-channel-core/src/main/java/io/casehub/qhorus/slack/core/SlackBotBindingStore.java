package io.casehub.qhorus.slack.core;

import java.util.Optional;
import java.util.UUID;

public interface SlackBotBindingStore {

    Optional<SlackBotBinding> findByChannelId(UUID channelId);

    void save(SlackBotBinding binding);

    void deleteByChannelId(UUID channelId);
}
