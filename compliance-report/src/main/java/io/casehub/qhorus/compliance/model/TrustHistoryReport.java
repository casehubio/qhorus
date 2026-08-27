package io.casehub.qhorus.compliance.model;

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
