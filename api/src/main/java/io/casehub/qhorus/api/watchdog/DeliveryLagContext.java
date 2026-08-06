package io.casehub.qhorus.api.watchdog;

import java.util.List;
import java.util.UUID;

public record DeliveryLagContext(
        UUID channelId,
        String channelName,
        List<LagDetail> laggingMembers,
        long latestMessageId
) implements AlertContext {

    public record LagDetail(String memberId, long lastDeliveredId, long lag) {}

    @Override
    public WatchdogConditionType conditionType() {
        return WatchdogConditionType.DELIVERY_LAG;
    }
}
