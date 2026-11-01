package io.casehub.qhorus.api.watchdog;

import java.util.List;
import java.util.UUID;

public sealed interface AlertContext
        permits BarrierStuckContext, ApprovalPendingContext,
                AgentStaleContext, ChannelIdleContext, QueueDepthContext,
                ContextPressureContext,
                LoopDetectedContext, ObligationFanOutContext,
                ConversationStallContext, EchoChamberContext,
                CircularDelegationContext, DeliveryLagContext {

    WatchdogConditionType conditionType();

    default List<String> affectedAgentIds() { return List.of(); }

    default UUID orgUnitId() { return null; }
}
