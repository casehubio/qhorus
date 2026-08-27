package io.casehub.qhorus.compliance.model;

import java.time.Instant;
import java.util.UUID;

public record AttestationSummaryEntry(
        UUID entryId,
        String verdict,
        double confidence,
        String attestorId,
        Instant occurredAt) {
}
