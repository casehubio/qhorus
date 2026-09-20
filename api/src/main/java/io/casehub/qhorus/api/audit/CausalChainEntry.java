package io.casehub.qhorus.api.audit;

import java.time.Instant;
import java.util.UUID;

public record CausalChainEntry(
        UUID entryId,
        UUID channelId,
        String channelName,
        String messageType,
        String actorId,
        String correlationId,
        Instant occurredAt,
        String content,
        UUID causedByEntryId) {
}
