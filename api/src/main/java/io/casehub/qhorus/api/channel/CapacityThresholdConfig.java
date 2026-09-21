package io.casehub.qhorus.api.channel;

public record CapacityThresholdConfig(
        String channelName,
        Double configured,
        double effective) {}
