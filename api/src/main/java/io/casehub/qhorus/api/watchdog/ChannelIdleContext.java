package io.casehub.qhorus.api.watchdog;

import java.util.List;

public record ChannelIdleContext(
        List<String> channelNames,
        long thresholdSeconds) implements AlertContext {

    @Override
    public WatchdogConditionType conditionType() { return WatchdogConditionType.CHANNEL_IDLE; }
}
