package io.casehub.qhorus.api.channel;

public record ClearChannelResult(
        String channelName,
        int messagesDeleted,
        boolean cleared) {}
