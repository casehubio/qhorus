package io.casehub.qhorus.api.channel;

import java.util.UUID;

public record UnreadCount(
        UUID channelId,
        String channelName,
        long count,
        Long latestMessageId) {}
