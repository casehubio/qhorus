package io.casehub.qhorus.api.watchdog;

import java.util.List;
import java.util.UUID;

public record BarrierStuckContext(
        UUID channelId,
        String channelName,
        List<String> missingContributors,
        long elapsedSeconds) implements AlertContext {

    @Override
    public WatchdogConditionType conditionType() { return WatchdogConditionType.BARRIER_STUCK; }
}
