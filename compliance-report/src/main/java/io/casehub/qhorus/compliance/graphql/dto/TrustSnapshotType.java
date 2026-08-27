package io.casehub.qhorus.compliance.graphql.dto;

import io.casehub.qhorus.compliance.model.TrustSnapshot;
import org.eclipse.microprofile.graphql.Type;

import java.time.Instant;

@Type("TrustSnapshot")
public record TrustSnapshotType(
        Instant snapshotAt,
        double score,
        String scoreType,
        String capabilityTag) {

    public static TrustSnapshotType from(TrustSnapshot s) {
        return new TrustSnapshotType(s.snapshotAt(), s.score(), s.scoreType(), s.capabilityTag());
    }
}
