package io.casehub.qhorus.compliance.schedule;

import io.casehub.qhorus.compliance.model.ReportType;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record ComplianceReportGeneratedEvent(
        UUID reportId,
        ReportType reportType,
        String tenancyId,
        UUID artefactId,
        Instant generatedAt,
        UUID scheduleId,
        String requestedBy,
        Map<String, String> requestParameters) {
}
