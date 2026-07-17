package io.casehub.qhorus.api.watchdog;

public enum WatchdogConditionType {
    BARRIER_STUCK, APPROVAL_PENDING, AGENT_STALE, CHANNEL_IDLE, QUEUE_DEPTH,
    CONTEXT_PRESSURE
}
