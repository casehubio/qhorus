package io.casehub.qhorus.compliance.verification;

import java.time.Instant;

public interface RemediatingProperty extends VerificationProperty {
    int remediate(String tenancyId, Instant from, Instant to);
}
