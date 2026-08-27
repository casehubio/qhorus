package io.casehub.qhorus.compliance.graphql.dto;

import io.casehub.qhorus.compliance.model.ProvenanceReport;
import org.eclipse.microprofile.graphql.Type;

import java.time.Instant;

@Type("ProvenanceReport")
public record ProvenanceReportType(
        String correlationId,
        Instant generatedAt,
        int schemaVersion) {

    public static ProvenanceReportType from(ProvenanceReport r) {
        return new ProvenanceReportType(r.correlationId(), r.generatedAt(), r.schemaVersion());
    }
}
