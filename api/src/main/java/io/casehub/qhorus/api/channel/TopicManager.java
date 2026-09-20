package io.casehub.qhorus.api.channel;

import io.casehub.qhorus.api.message.Topic;
import io.casehub.qhorus.api.message.TopicSummary;

import java.util.List;
import java.util.UUID;

public interface TopicManager {

    record RenameResult(String oldName, String newName, int messagesUpdated) {}

    record MergeResult(String sourceTopic, String targetTopic, int messagesUpdated) {}

    record MoveResult(String topicName, UUID sourceChannelId, UUID targetChannelId, int messagesUpdated) {}


    Topic create(UUID channelId, String name);

    Topic resolve(UUID channelId, String topicName);

    Topic resolve(UUID channelId, String topicName, String actorId);


    Topic unresolve(UUID channelId, String topicName);

    RenameResult rename(UUID channelId, String oldName, String newName);

    RenameResult rename(UUID channelId, String oldName, String newName, String actorId);


    MergeResult merge(UUID channelId, String sourceTopic, String targetTopic);

    MergeResult merge(UUID channelId, String sourceTopic, String targetTopic, String actorId);


    List<TopicSummary> listTopics(UUID channelId);

    MoveResult move(UUID sourceChannelId, String topicName, UUID targetChannelId);
}
