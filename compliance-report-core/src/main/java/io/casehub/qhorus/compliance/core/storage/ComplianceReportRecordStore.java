package io.casehub.qhorus.compliance.core.storage;

import io.casehub.qhorus.api.compliance.report.ReportType;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ComplianceReportRecordStore {

    ComplianceReportRecord save(ComplianceReportRecord record);

    Optional<ComplianceReportRecord> findById(UUID id);

    List<ComplianceReportRecord> findByType(ReportType type, String tenancyId, int limit);

    List<ComplianceReportRecord> findByTimeRange(Instant from, Instant to, String tenancyId, int limit);

    void delete(UUID id);

    List<ComplianceReportRecord> findOlderThan(Instant cutoff);
}
