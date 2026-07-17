package io.casehub.qhorus.api.watchdog;

import java.util.UUID;

public record ContextPressureContext(
        UUID channelId,
        String channelName,
        String actorId,
        int contextWindowPct
) implements AlertContext {
    @Override
    public WatchdogConditionType conditionType() {
        return WatchdogConditionType.CONTEXT_PRESSURE;
    }
}
