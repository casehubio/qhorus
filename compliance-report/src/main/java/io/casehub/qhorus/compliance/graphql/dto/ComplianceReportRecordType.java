package io.casehub.qhorus.compliance.graphql.dto;

import io.casehub.qhorus.compliance.model.ReportFormat;
import io.casehub.qhorus.compliance.model.ReportType;
import io.casehub.qhorus.compliance.storage.ComplianceReportRecord;
import org.eclipse.microprofile.graphql.Type;

import java.time.Instant;
import java.util.UUID;

@Type("ComplianceReportRecord")
public record ComplianceReportRecordType(
        UUID id,
        ReportType reportType,
        String tenancyId,
        Instant generatedAt,
        UUID scheduleId,
        UUID artefactId,
        ReportFormat format,
        int schemaVersion) {

    public static ComplianceReportRecordType from(ComplianceReportRecord r) {
        return new ComplianceReportRecordType(
                r.id, r.reportType, r.tenancyId, r.generatedAt,
                r.scheduleId, r.artefactId, r.format, r.schemaVersion);
    }
}
