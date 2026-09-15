package io.casehub.qhorus.api.compliance.report;

import java.time.Instant;
import java.util.Map;

public record ProvenanceReport(
        String correlationId,
        Map<String, Object> provJsonLd,
        Instant generatedAt,
        int schemaVersion) {
}
