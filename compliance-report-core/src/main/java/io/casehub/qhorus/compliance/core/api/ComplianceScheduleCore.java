package io.casehub.qhorus.compliance.core.api;

import io.casehub.qhorus.api.compliance.report.ReportFormat;
import io.casehub.qhorus.api.compliance.report.ReportType;
import io.casehub.qhorus.compliance.core.schedule.ComplianceReportSchedule;
import io.casehub.qhorus.compliance.core.schedule.ComplianceReportScheduleStore;

import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

public class ComplianceScheduleCore {

    private final ComplianceReportScheduleStore scheduleStore;
    private final Supplier<String> tenancyIdSupplier;

    public ComplianceScheduleCore(ComplianceReportScheduleStore scheduleStore,
                                   Supplier<String> tenancyIdSupplier) {
        this.scheduleStore = scheduleStore;
        this.tenancyIdSupplier = tenancyIdSupplier;
    }

    public List<ComplianceReportSchedule> list() {
        return scheduleStore.findByTenancy(tenancyIdSupplier.get());
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
        schedule.tenancyId = tenancyIdSupplier.get();
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
