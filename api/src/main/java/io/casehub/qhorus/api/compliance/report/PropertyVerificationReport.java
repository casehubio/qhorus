package io.casehub.qhorus.api.compliance.report;

import java.time.Instant;
import java.util.List;

public record PropertyVerificationReport(
        Instant from,
        Instant to,
        List<PropertyResult> results,
        int totalProperties,
        int passed,
        int violated,
        int remediationsApplied,
        String merkleRoot,
        Instant generatedAt,
        int schemaVersion
) {}
