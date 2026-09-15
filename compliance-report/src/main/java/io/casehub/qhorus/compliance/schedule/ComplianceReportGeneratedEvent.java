package io.casehub.qhorus.compliance.schedule;

import io.casehub.qhorus.api.compliance.report.ReportType;

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
        Map<String, String> requestParameters,
        String signatureStatus,
        UUID signatureArtefactId) {

    public ComplianceReportGeneratedEvent(UUID reportId, ReportType reportType,
                                          String tenancyId, UUID artefactId,
                                          Instant generatedAt, UUID scheduleId,
                                          String requestedBy,
                                          Map<String, String> requestParameters) {
        this(reportId, reportType, tenancyId, artefactId, generatedAt,
                scheduleId, requestedBy, requestParameters, "UNSIGNED", null);
    }
}
