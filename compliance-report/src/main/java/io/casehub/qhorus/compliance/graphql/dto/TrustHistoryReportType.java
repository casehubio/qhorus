package io.casehub.qhorus.compliance.graphql.dto;

import io.casehub.qhorus.compliance.model.TrustHistoryReport;
import org.eclipse.microprofile.graphql.Type;

import java.time.Instant;
import java.util.List;

@Type("TrustHistoryReport")
public record TrustHistoryReportType(
        Instant from,
        Instant to,
        List<ActorTrustTrajectoryType> actors,
        String merkleRoot,
        Instant generatedAt,
        int schemaVersion) {

    public static TrustHistoryReportType from(TrustHistoryReport r) {
        return new TrustHistoryReportType(
                r.from(), r.to(),
                r.actors().stream().map(ActorTrustTrajectoryType::from).toList(),
                r.merkleRoot(), r.generatedAt(), r.schemaVersion());
    }
}
