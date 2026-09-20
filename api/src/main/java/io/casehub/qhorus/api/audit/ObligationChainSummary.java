package io.casehub.qhorus.api.audit;

import io.casehub.qhorus.api.message.Commitment;

import java.time.Instant;
import java.util.List;

public record ObligationChainSummary(
        String correlationId,
        String initiator,
        Instant createdAt,
        Instant resolvedAt,
        Long elapsedSeconds,
        String resolution,
        List<String> participants,
        int handoffCount,
        Commitment commitment) {
}
