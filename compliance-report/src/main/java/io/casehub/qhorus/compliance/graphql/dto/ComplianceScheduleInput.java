package io.casehub.qhorus.compliance.graphql.dto;

import io.casehub.qhorus.api.compliance.report.ReportFormat;
import io.casehub.qhorus.api.compliance.report.ReportType;
import org.eclipse.microprofile.graphql.Input;

import java.util.UUID;

@Input("ComplianceScheduleInput")
public record ComplianceScheduleInput(
        ReportType reportType,
        UUID channelId,
        String scheduleJson,
        ReportFormat format) {
}
