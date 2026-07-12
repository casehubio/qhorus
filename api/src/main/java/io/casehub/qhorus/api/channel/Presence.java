package io.casehub.qhorus.api.channel;

import java.time.Instant;

public record Presence(
        String memberId,
        PresenceStatus status,
        PresenceStatus reportedStatus,
        Instant lastSeenAt,
        String statusMessage
) {}
