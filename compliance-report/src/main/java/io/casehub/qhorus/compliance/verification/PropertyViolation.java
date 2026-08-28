package io.casehub.qhorus.compliance.verification;

import java.time.Instant;

public record PropertyViolation(
        String propertyName,
        String description,
        String evidence,
        Instant occurredAt,
        String severity
) {}
