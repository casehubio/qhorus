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
        List<io.casehub.qhorus.api.spi.DispatchAdvisory> violations,
        List<String> violationSources,
        boolean severityUpgrade) {

    public EnforcementBlockedEvent {
        violations       = violations != null ? List.copyOf(violations) : List.of();
        violationSources = violationSources != null ? List.copyOf(violationSources) : List.of();
    }

    public EnforcementBlockedEvent(UUID channelId, String channelName, EnforcementMode mode,
                                   String blockedSender, MessageType blockedType,
                                   List<io.casehub.qhorus.api.spi.DispatchAdvisory> violations,
                                   List<String> violationSources) {
        this(channelId, channelName, mode, blockedSender, blockedType,
             violations, violationSources, false);
    }
}
