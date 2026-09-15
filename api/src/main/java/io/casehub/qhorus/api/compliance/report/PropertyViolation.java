package io.casehub.qhorus.api.compliance.report;

import java.time.Instant;

public record PropertyViolation(
        String propertyName,
        String description,
        String evidence,
        Instant occurredAt,
        String severity
) {}
