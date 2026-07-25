package io.casehub.qhorus.api.store;

import io.casehub.qhorus.api.message.Topic;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TopicReader {

    Optional<Topic> find(UUID channelId, String name);

    Optional<Topic> findById(Long id);

    List<Topic> findByChannel(UUID channelId);
}
