package io.casehub.qhorus.api.audit;

import java.time.Instant;

public record TimelineEntry(
        Long id,
        String type,
        Instant createdAt,
        String sender,
        String messageType,
        String content,
        String correlationId,
        Long inReplyTo,
        String target,
        int replyCount,
        String topic,
        Instant deadline,
        String toolName,
        Long durationMs,
        Long tokenCount) {
}
