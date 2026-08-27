package io.casehub.qhorus.compliance.graphql.dto;

import io.casehub.qhorus.compliance.model.ActorTrustTrajectory;
import org.eclipse.microprofile.graphql.Type;

import java.util.List;

@Type("ActorTrustTrajectory")
public record ActorTrustTrajectoryType(
        String actorId,
        Double currentScore,
        List<TrustSnapshotType> trajectory,
        List<AttestationSummaryEntryType> attestations) {

    public static ActorTrustTrajectoryType from(ActorTrustTrajectory t) {
        return new ActorTrustTrajectoryType(
                t.actorId(), t.currentScore(),
                t.trajectory().stream().map(TrustSnapshotType::from).toList(),
                t.attestations().stream().map(AttestationSummaryEntryType::from).toList());
    }
}
