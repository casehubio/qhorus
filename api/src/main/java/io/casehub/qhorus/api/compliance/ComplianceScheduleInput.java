package io.casehub.qhorus.api.compliance;

import io.casehub.qhorus.api.compliance.report.ReportFormat;
import io.casehub.qhorus.api.compliance.report.ReportType;

import java.util.UUID;

public record ComplianceScheduleInput(
    ReportType reportType,
    UUID channelId,
    String scheduleJson,
    ReportFormat format
) {}
