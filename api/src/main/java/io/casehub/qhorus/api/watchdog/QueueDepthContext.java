package io.casehub.qhorus.api.watchdog;

public record QueueDepthContext(
        String channelName,
        long messageCount,
        int threshold) implements AlertContext {

    @Override
    public WatchdogConditionType conditionType() { return WatchdogConditionType.QUEUE_DEPTH; }
}
