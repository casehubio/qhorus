package io.casehub.qhorus.compliance.report;

import io.casehub.ledger.runtime.service.TrustGateService;
import io.casehub.qhorus.compliance.model.ActorTrustTrajectory;
import io.casehub.qhorus.compliance.model.TrustHistoryReport;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.time.Instant;
import java.util.List;
import java.util.OptionalDouble;

@ApplicationScoped
public class TrustHistoryReportService {

    @Inject Instance<TrustGateService> trustGateServiceInstance;

    public TrustHistoryReport generate(String actorId, Instant from, Instant to, String tenancyId) {
        Double currentScore = null;
        if (trustGateServiceInstance.isResolvable()) {
            OptionalDouble score = trustGateServiceInstance.get().currentScore(actorId);
            if (score.isPresent()) {
                currentScore = score.getAsDouble();
            }
        }

        ActorTrustTrajectory trajectory = new ActorTrustTrajectory(
                actorId, currentScore, List.of(), List.of());

        return new TrustHistoryReport(from, to, List.of(trajectory), null, Instant.now(), 1);
    }
}
