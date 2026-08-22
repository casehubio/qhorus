package io.casehub.qhorus.api.watchdog;

import java.util.List;
import java.util.UUID;

public record LoopDetectedContext(
        UUID channelId, String channelName,
        String sender, int messageCount, double maxSimilarity
) implements AlertContext {
    @Override
    public WatchdogConditionType conditionType() { return WatchdogConditionType.LOOP_DETECTED; }
    @Override
    public List<String> affectedAgentIds() { return List.of(sender); }
}