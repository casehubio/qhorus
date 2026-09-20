package io.casehub.qhorus.api.audit;

import java.time.Instant;
import java.util.UUID;

public record AttestationSummary(
        UUID attestationId,
        String verdict,
        String attestorId,
        String attestorRole,
        String evidence,
        double confidence,
        Instant occurredAt) {
}
