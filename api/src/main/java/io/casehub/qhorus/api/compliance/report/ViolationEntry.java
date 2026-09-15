package io.casehub.qhorus.api.compliance.report;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record ViolationEntry(
        Instant occurredAt,
        String sender,
        String messageType,
        String enforcementMode,
        List<String> violationSources,
        List<String> violations,
        String action,
        UUID ledgerEntryId) {
}
