package io.casehub.qhorus.api.watchdog;

public sealed interface AlertContext
        permits BarrierStuckContext, ApprovalPendingContext,
                AgentStaleContext, ChannelIdleContext, QueueDepthContext {

    WatchdogConditionType conditionType();
}
