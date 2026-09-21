package io.casehub.qhorus.api.channel;

public record ChannelRoutingConfig(
        String channelName,
        Double trustThreshold,
        double effectiveThreshold,
        double globalDefault) {}
