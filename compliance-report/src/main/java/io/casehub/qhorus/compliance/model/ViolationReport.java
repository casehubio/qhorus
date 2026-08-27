package io.casehub.qhorus.compliance.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record ViolationReport(
        Instant from,
        Instant to,
        UUID channelId,
        String channelName,
        List<ViolationEntry> violations,
        int totalBlocked,
        int totalAdvisory,
        int totalQuarantined,
        Map<String, Integer> violationsBySource,
        String merkleRoot,
        Instant generatedAt,
        int schemaVersion) {
}
