package io.casehub.qhorus.compliance.verification;

import io.casehub.qhorus.api.message.Commitment;
import io.casehub.qhorus.api.store.CommitmentStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class LivenessProperty implements VerificationProperty {

    static final Duration DEFAULT_THRESHOLD = Duration.ofHours(24);

    @Inject public CommitmentStore commitmentStore;

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
