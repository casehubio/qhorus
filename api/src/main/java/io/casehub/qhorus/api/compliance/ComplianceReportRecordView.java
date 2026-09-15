package io.casehub.qhorus.api.compliance;

import io.casehub.qhorus.api.compliance.report.ReportFormat;
import io.casehub.qhorus.api.compliance.report.ReportType;

import java.time.Instant;
import java.util.UUID;

public record ComplianceReportRecordView(
    UUID id,
    ReportType reportType,
    String tenancyId,
    Instant generatedAt,
    UUID scheduleId,
    UUID artefactId,
    ReportFormat format,
    int schemaVersion
) {}
