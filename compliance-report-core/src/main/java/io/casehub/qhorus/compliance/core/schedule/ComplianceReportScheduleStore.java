package io.casehub.qhorus.compliance.core.schedule;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComplianceReportScheduleStore {

    ComplianceReportSchedule save(ComplianceReportSchedule schedule);

    Optional<ComplianceReportSchedule> findById(UUID id);

    List<ComplianceReportSchedule> findByTenancy(String tenancyId);

    List<ComplianceReportSchedule> findEnabled();

    void updateLastRunAt(UUID id, Instant lastRunAt);

    void delete(UUID id);
}
