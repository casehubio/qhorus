package io.casehub.qhorus.api.channel;

import java.time.Instant;
import java.util.UUID;

public record PresenceChangedEvent(
        String memberId,
        UUID channelId,
        PresenceStatus status,
        PresenceStatus previousStatus,
        Instant occurredAt) {}
