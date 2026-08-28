package io.casehub.qhorus.compliance.graphql.dto;

import io.casehub.qhorus.compliance.model.PropertyVerificationReport;

import java.time.Instant;
import java.util.List;

public record PropertyVerificationReportType(
        Instant from,
        Instant to,
        List<PropertyResultType> results,
        int totalProperties,
        int passed,
        int violated,
        int remediationsApplied,
        String merkleRoot,
        Instant generatedAt,
        int schemaVersion
) {
    public static PropertyVerificationReportType from(PropertyVerificationReport r) {
        return new PropertyVerificationReportType(
                r.from(), r.to(),
                r.results().stream().map(PropertyResultType::from).toList(),
                r.totalProperties(), r.passed(), r.violated(),
                r.remediationsApplied(),
                r.merkleRoot(), r.generatedAt(), r.schemaVersion());
    }
}
