package io.casehub.qhorus.api.channel;

public record TopicRenameResult(String oldName, String newName, int messagesUpdated) {}
