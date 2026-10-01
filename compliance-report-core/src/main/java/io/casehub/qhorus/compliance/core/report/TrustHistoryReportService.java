package io.casehub.qhorus.compliance.core.report;

import io.casehub.ledger.core.trust.TrustGateService;
import io.casehub.qhorus.api.compliance.report.ActorTrustTrajectory;
import io.casehub.qhorus.api.compliance.report.TrustHistoryReport;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

public class TrustHistoryReportService {

    private final Optional<TrustGateService> trustGateService;

    public TrustHistoryReportService(Optional<TrustGateService> trustGateService) {
        this.trustGateService = trustGateService;
    }

    public TrustHistoryReport generate(String actorId, Instant from, Instant to, String tenancyId) {
        Double currentScore = null;
        if (trustGateService.isPresent()) {
            OptionalDouble score = trustGateService.get().currentScore(actorId);
            if (score.isPresent()) {
                currentScore = score.getAsDouble();
            }
        }

        ActorTrustTrajectory trajectory = new ActorTrustTrajectory(
                actorId, currentScore, List.of(), List.of());

        return new TrustHistoryReport(from, to, List.of(trajectory), null, Instant.now(), 1);
    }
}
