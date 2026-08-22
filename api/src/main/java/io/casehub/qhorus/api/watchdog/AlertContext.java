package io.casehub.qhorus.api.watchdog;

import java.util.List;

public sealed interface AlertContext
        permits BarrierStuckContext, ApprovalPendingContext,
                AgentStaleContext, ChannelIdleContext, QueueDepthContext,
                ContextPressureContext,
                LoopDetectedContext, ObligationFanOutContext,
                ConversationStallContext, EchoChamberContext,
                CircularDelegationContext, DeliveryLagContext {

    WatchdogConditionType conditionType();

    default List<String> affectedAgentIds() { return List.of(); }
}
