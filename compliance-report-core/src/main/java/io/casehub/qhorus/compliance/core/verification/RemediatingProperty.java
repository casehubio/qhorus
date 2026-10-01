package io.casehub.qhorus.compliance.core.verification;

import java.time.Instant;

public interface RemediatingProperty extends VerificationProperty {
    int remediate(String tenancyId, Instant from, Instant to);
}
