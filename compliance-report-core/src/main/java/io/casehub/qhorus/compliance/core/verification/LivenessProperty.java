package io.casehub.qhorus.compliance.core.verification;

import io.casehub.qhorus.api.compliance.report.PropertyViolation;
import io.casehub.qhorus.api.message.Commitment;
import io.casehub.qhorus.api.store.CommitmentStore;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

public class LivenessProperty implements VerificationProperty {

    static final Duration DEFAULT_THRESHOLD = Duration.ofHours(24);

    private final CommitmentStore commitmentStore;

    public LivenessProperty(CommitmentStore commitmentStore) {
        this.commitmentStore = commitmentStore;
    }

    @Override
    public String name() {
        return "LIVENESS";
    }

    @Override
    public String ctlFormula() {
        return "AG(OPEN → AF(FULFILLED ∨ DECLINED ∨ FAILED ∨ DELEGATED ∨ EXPIRED))";
    }

    @Override
    public String description() {
        return "Every commitment eventually resolves — no indefinite OPEN state.";
    }

    @Override
    public CheckResult check(String tenancyId, Instant from, Instant to) {
        Instant cutoff = to.minus(DEFAULT_THRESHOLD);
        List<Commitment> stale = commitmentStore.findOpenOlderThan(cutoff, tenancyId);
        List<PropertyViolation> violations = stale.stream()
                                                  .map(c -> new PropertyViolation(
                        name(),
                        "Commitment OPEN for >" + DEFAULT_THRESHOLD + " without resolution",
                        "correlationId=" + c.correlationId() + " state=" + c.state()
                                + " createdAt=" + c.createdAt(),
                        c.createdAt(),
                        "HIGH"))
                                                  .toList();
        return new CheckResult(violations, 0);
    }
}
