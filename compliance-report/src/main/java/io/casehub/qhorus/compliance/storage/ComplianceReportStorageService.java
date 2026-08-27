package io.casehub.qhorus.compliance.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.qhorus.api.store.DataStore;
import io.casehub.qhorus.compliance.model.ReportFormat;
import io.casehub.qhorus.compliance.model.ReportType;
import io.casehub.qhorus.runtime.data.DataService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class ComplianceReportStorageService {

    @Inject DataService dataService;
    @Inject ComplianceReportRecordStore recordStore;
    @Inject ObjectMapper objectMapper;

    @Transactional
    public ComplianceReportRecord store(ReportType reportType, Object report,
                                         ReportFormat format, UUID scheduleId,
                                         String tenancyId) {
        try {
            String json = objectMapper.writeValueAsString(report);
            Instant now = Instant.now();
            String key = "compliance-report/" + reportType.name() + "/" + tenancyId + "/" + now;

            var artefact = dataService.store(key,
                    "Compliance " + reportType.name() + " report for tenant " + tenancyId,
                    "system:compliance-scheduler", json, false, true);

            ComplianceReportRecord record = new ComplianceReportRecord();
            record.id = UUID.randomUUID();
            record.reportType = reportType;
            record.tenancyId = tenancyId;
            record.generatedAt = now;
            record.scheduleId = scheduleId;
            record.artefactId = artefact.id();
            record.schemaVersion = 1;
            record.format = format;

            return recordStore.save(record);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to store compliance report", e);
        }
    }

    public Optional<String> retrieveJson(UUID reportId) {
        return recordStore.findById(reportId)
                .flatMap(record -> dataService.getByUuid(record.artefactId))
                .map(data -> data.content());
    }

    @Transactional
    public void delete(UUID reportId) {
        recordStore.findById(reportId).ifPresent(record -> {
            dataService.getByUuid(record.artefactId).ifPresent(data ->
                    dataService.release(data.id(), null));
            recordStore.delete(reportId);
        });
    }
}
