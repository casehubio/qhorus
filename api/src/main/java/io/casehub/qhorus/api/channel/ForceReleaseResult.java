package io.casehub.qhorus.api.channel;

import io.casehub.qhorus.api.message.Message;

import java.util.List;

public record ForceReleaseResult(
        String channelName,
        String semantic,
        int messageCount,
        List<Message> messages) {}
