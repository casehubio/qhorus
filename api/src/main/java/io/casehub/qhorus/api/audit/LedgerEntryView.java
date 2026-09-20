package io.casehub.qhorus.api.audit;

import java.time.Instant;
import java.util.UUID;

public record LedgerEntryView(
        UUID entryId,
        long sequenceNumber,
        String messageType,
        String entryType,
        String actorId,
        String target,
        String content,
        String correlationId,
        UUID commitmentId,
        UUID causedByEntryId,
        Instant occurredAt,
        Long messageId,
        String toolName,
        Long durationMs,
        Long tokenCount,
        String contextRefs,
        String sourceEntity,
        UUID channelId,
        String channelName) {
}
