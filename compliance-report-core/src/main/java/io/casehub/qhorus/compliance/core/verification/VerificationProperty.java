package io.casehub.qhorus.compliance.core.verification;

import java.time.Instant;

public interface VerificationProperty {
    String name();
    String ctlFormula();
    String description();
    CheckResult check(String tenancyId, Instant from, Instant to);
}
