package io.casehub.qhorus.api.compliance;

import io.casehub.qhorus.api.compliance.report.ReportFormat;
import io.casehub.qhorus.api.compliance.report.ReportType;

import java.time.Instant;
import java.util.UUID;

public record ComplianceScheduleView(
    UUID id,
    ReportType reportType,
    UUID channelId,
    String scheduleJson,
    ReportFormat format,
    String tenancyId,
    boolean enabled,
    Instant lastRunAt,
    Instant createdAt
) {}
