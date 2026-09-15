package io.casehub.qhorus.api.compliance.report;

import java.time.Instant;
import java.util.UUID;

public record AttestationSummaryEntry(
        UUID entryId,
        String verdict,
        double confidence,
        String attestorId,
        Instant occurredAt) {
}
