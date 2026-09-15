package io.casehub.qhorus.api.channel;

import java.util.UUID;

public record ChannelQuery(
    String keyword,
    String namePrefix,
    ChannelSemantic semantic,
    Boolean paused,
    UUID spaceId,
    Integer offset,
    Integer limit,
    String cursor
) {}
