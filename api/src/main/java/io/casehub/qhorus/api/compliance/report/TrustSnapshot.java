package io.casehub.qhorus.api.compliance.report;

import java.time.Instant;

public record TrustSnapshot(
        Instant snapshotAt,
        double score,
        String scoreType,
        String capabilityTag) {
}
