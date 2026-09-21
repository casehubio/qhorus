package io.casehub.qhorus.api.channel;

import java.util.Map;

public record ChannelSummaryResult(
        String channelName,
        String content,
        Map<String, String> annotations,
        String updatedAt,
        String updatedBy,
        Integer updateAfterMessages,
        Integer updateAfterSeconds) {}
