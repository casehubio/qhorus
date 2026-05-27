package io.casehub.qhorus.api.watchdog;

import java.time.Instant;
import java.util.UUID;

public record WatchdogAlertEvent(
        UUID watchdogId,
        String targetName,
        String notificationChannel,
        String summary,
        Instant firedAt,
        AlertContext context) {

    public WatchdogConditionType conditionType() {
        return context.conditionType();
    }
}
