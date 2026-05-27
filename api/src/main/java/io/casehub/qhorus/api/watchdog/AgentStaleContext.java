package io.casehub.qhorus.api.watchdog;

import java.util.List;

public record AgentStaleContext(
        long staleCount,
        List<String> staleInstanceIds) implements AlertContext {

    @Override
    public WatchdogConditionType conditionType() { return WatchdogConditionType.AGENT_STALE; }
}
