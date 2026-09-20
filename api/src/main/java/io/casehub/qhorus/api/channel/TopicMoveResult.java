package io.casehub.qhorus.api.channel;

import java.util.UUID;

public record TopicMoveResult(String topicName, UUID sourceChannelId, UUID targetChannelId, int messagesUpdated) {}
