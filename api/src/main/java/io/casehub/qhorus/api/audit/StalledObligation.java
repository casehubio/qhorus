package io.casehub.qhorus.api.audit;

import java.time.Instant;

public record StalledObligation(
        String correlationId,
        String actorId,
        String content,
        Instant occurredAt,
        long stalledForSeconds) {
}
