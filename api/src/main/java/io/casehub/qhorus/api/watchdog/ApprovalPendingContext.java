package io.casehub.qhorus.api.watchdog;

import java.time.Instant;

public record ApprovalPendingContext(
        long pendingCount,
        Instant oldestExpiryAt) implements AlertContext {

    @Override
    public WatchdogConditionType conditionType() { return WatchdogConditionType.APPROVAL_PENDING; }
}
