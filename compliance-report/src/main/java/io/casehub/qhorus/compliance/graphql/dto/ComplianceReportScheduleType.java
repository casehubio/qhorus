package io.casehub.qhorus.compliance.graphql.dto;

import io.casehub.qhorus.compliance.model.ReportFormat;
import io.casehub.qhorus.compliance.model.ReportType;
import io.casehub.qhorus.compliance.schedule.ComplianceReportSchedule;
import org.eclipse.microprofile.graphql.Type;

import java.time.Instant;
import java.util.UUID;

@Type("ComplianceReportSchedule")
public record ComplianceReportScheduleType(
        UUID id,
        ReportType reportType,
        UUID channelId,
        String scheduleJson,
        ReportFormat format,
        String tenancyId,
        boolean enabled,
        Instant lastRunAt,
        Instant createdAt) {

    public static ComplianceReportScheduleType from(ComplianceReportSchedule s) {
        return new ComplianceReportScheduleType(
                s.id, s.reportType, s.channelId, s.scheduleJson,
                s.format, s.tenancyId, s.enabled, s.lastRunAt, s.createdAt);
    }
}
