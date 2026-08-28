package io.casehub.qhorus.compliance.graphql.dto;

import io.casehub.qhorus.compliance.verification.PropertyViolation;

import java.time.Instant;

public record PropertyViolationType(
        String propertyName,
        String description,
        String evidence,
        Instant occurredAt,
        String severity
) {
    public static PropertyViolationType from(PropertyViolation v) {
        return new PropertyViolationType(
                v.propertyName(), v.description(), v.evidence(),
                v.occurredAt(), v.severity());
    }
}
