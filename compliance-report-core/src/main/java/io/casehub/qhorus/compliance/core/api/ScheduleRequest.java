package io.casehub.qhorus.compliance.core.api;

import io.casehub.qhorus.api.compliance.report.ReportFormat;
import io.casehub.qhorus.api.compliance.report.ReportType;

import java.util.UUID;

public record ScheduleRequest(ReportType reportType, UUID channelId, String schedule, ReportFormat format) {
}
