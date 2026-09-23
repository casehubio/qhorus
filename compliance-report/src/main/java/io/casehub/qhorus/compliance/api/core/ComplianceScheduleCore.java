package io.casehub.qhorus.compliance.api.core;

import io.casehub.qhorus.api.compliance.report.ReportFormat;
import io.casehub.qhorus.api.compliance.report.ReportType;
import io.casehub.qhorus.compliance.schedule.ComplianceReportSchedule;
import io.casehub.qhorus.compliance.schedule.ComplianceReportScheduleStore;
import io.casehub.qhorus.runtime.identity.InboundTenancyContext;

import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class ComplianceScheduleCore {

    private final ComplianceReportScheduleStore scheduleStore;
    private final InboundTenancyContext tenancyContext;

    public ComplianceScheduleCore(ComplianceReportScheduleStore scheduleStore,
                                   InboundTenancyContext tenancyContext) {
        this.scheduleStore = scheduleStore;
        this.tenancyContext = tenancyContext;
    }

    public List<ComplianceReportSchedule> list() {
        return scheduleStore.findByTenancy(tenancyContext.tenancyId());
    }

    @Transactional
    public ComplianceReportSchedule create(ScheduleRequest request) {
        if (request.reportType() == ReportType.VIOLATION && request.channelId() == null) {
            throw new IllegalArgumentException("channelId is required for VIOLATION report schedules");
        }
        if (request.reportType() == ReportType.JUDGMENT_ATTRIBUTION) {
            throw new IllegalArgumentException("JUDGMENT_ATTRIBUTION is on-demand only — not schedulable");
        }

        ComplianceReportSchedule schedule = new ComplianceReportSchedule();
        schedule.id = UUID.randomUUID();
        schedule.reportType = request.reportType();
        schedule.channelId = request.channelId();
        schedule.scheduleJson = request.schedule();
        schedule.format = request.format() != null ? request.format() : ReportFormat.JSON;
        schedule.tenancyId = tenancyContext.tenancyId();
        schedule.enabled = true;

        scheduleStore.save(schedule);
        return schedule;
    }

    @Transactional
    public Optional<ComplianceReportSchedule> update(UUID id, ScheduleUpdateRequest request) {
        return scheduleStore.findById(id)
                .map(schedule -> {
                    if (request.schedule() != null) schedule.scheduleJson = request.schedule();
                    if (request.format() != null) schedule.format = request.format();
                    if (request.enabled() != null) schedule.enabled = request.enabled();
                    scheduleStore.save(schedule);
                    return schedule;
                });
    }

    @Transactional
    public void delete(UUID id) {
        scheduleStore.delete(id);
    }
}
