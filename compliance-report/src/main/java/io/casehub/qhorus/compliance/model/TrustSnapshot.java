package io.casehub.qhorus.compliance.model;

import java.time.Instant;

public record TrustSnapshot(
        Instant snapshotAt,
        double score,
        String scoreType,
        String capabilityTag) {
}
