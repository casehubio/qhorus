package io.casehub.qhorus.api.channel;

import java.util.List;

public record ChannelPage(
    List<Channel> items,
    boolean hasNext,
    String cursor
) {}
