package io.casehub.qhorus.compliance.model;

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
