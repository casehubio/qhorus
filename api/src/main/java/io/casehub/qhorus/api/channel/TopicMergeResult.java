package io.casehub.qhorus.api.channel;

public record TopicMergeResult(String sourceTopic, String targetTopic, int messagesUpdated) {}
