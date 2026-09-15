package io.casehub.qhorus.api.compliance.report;

import java.time.Instant;
import java.util.List;

public record TrustHistoryReport(
        Instant from,
        Instant to,
        List<ActorTrustTrajectory> actors,
        String merkleRoot,
        Instant generatedAt,
        int schemaVersion) {
}
