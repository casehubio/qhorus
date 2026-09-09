package io.casehub.qhorus.api.watchdog;

import java.time.Instant;
import java.util.UUID;

/**
 * @param caseId Case UUID when the alert pertains to a specific case channel, null for
 *     cross-channel alerts. Resolved by consumers (engine bridge) when null.
 */
public record WatchdogAlertEvent(
        UUID watchdogId,
        String targetName,
        String notificationChannel,
        String summary,
        Instant firedAt,
        AlertContext context,
        UUID caseId) {

    public WatchdogAlertEvent(
            UUID watchdogId,
            String targetName,
            String notificationChannel,
            String summary,
            Instant firedAt,
            AlertContext context) {
        this(watchdogId, targetName, notificationChannel, summary, firedAt, context, null);
    }

    public WatchdogConditionType conditionType() {
        return context.conditionType();
    }
}
