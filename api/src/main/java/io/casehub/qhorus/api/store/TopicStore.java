package io.casehub.qhorus.api.store;

import io.casehub.qhorus.api.message.Topic;

import java.util.UUID;

public interface TopicStore extends TopicReader {
    Topic put(Topic topic);

    int rename(UUID channelId, String oldName, String newName);

    void delete(UUID channelId, String name);

    void deleteAll(UUID channelId);
}
