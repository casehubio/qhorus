package io.casehub.qhorus.api.store;

import io.casehub.qhorus.api.message.Message;

import java.util.UUID;

public interface MessageStore extends MessageReader {

    Message put(Message message);

    void deleteAll(UUID channelId);

    void deleteNonEvent(UUID channelId);

    void delete(Long id);

    int updateTopicName(UUID channelId, String oldTopic, String newTopic);

    int updateChannelId(UUID sourceChannelId, String topic, UUID targetChannelId);
}
