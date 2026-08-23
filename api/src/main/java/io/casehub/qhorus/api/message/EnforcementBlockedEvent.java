package io.casehub.qhorus.api.message;

import io.casehub.qhorus.api.channel.EnforcementMode;

import java.util.List;
import java.util.UUID;

public record EnforcementBlockedEvent(
        UUID channelId,
        String channelName,
        EnforcementMode mode,
        String blockedSender,
        MessageType blockedType,
        List<String> violations,
        List<String> violationSources) {

    public EnforcementBlockedEvent {
        violations = violations != null ? List.copyOf(violations) : List.of();
        violationSources = violationSources != null ? List.copyOf(violationSources) : List.of();
    }
}
