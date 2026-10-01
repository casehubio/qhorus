package io.casehub.qhorus.compliance.core.api;

import io.casehub.qhorus.api.compliance.report.ReportFormat;

public record ScheduleUpdateRequest(String schedule, ReportFormat format, Boolean enabled) {
}
