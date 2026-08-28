package io.casehub.qhorus.compliance.schedule;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.platform.api.delivery.DigestSchedule;
import io.casehub.qhorus.compliance.report.JudgmentFulfillmentReportService;
import io.casehub.qhorus.compliance.report.ObligationReportService;
import io.casehub.qhorus.compliance.report.ViolationReportService;
import io.casehub.qhorus.compliance.storage.ComplianceReportRecord;
import io.casehub.qhorus.compliance.storage.ComplianceReportStorageService;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.Map;

@ApplicationScoped
public class ComplianceReportScheduler {

    private static final Logger LOG = Logger.getLogger(ComplianceReportScheduler.class);

    @Inject ComplianceReportScheduleStore scheduleStore;
    @Inject ComplianceReportStorageService storageService;
    @Inject ObligationReportService obligationService;
    @Inject ViolationReportService violationService;
    @Inject
            JudgmentFulfillmentReportService judgmentFulfillmentService;

    @Inject Event<ComplianceReportGeneratedEvent> generatedEvent;
    @Inject ObjectMapper objectMapper;
    @Inject
    @org.eclipse.microprofile.config.inject.ConfigProperty(name = "casehub.qhorus.compliance.retention-days")
    java.util.Optional<Integer> retentionDays;


    @Scheduled(every = "1h")
    void sweep() {
        Instant now = Instant.now();
        for (ComplianceReportSchedule schedule : scheduleStore.findEnabled()) {
            try {
                DigestSchedule timing = objectMapper.readValue(schedule.scheduleJson, DigestSchedule.class);
                Instant lastRun = schedule.lastRunAt != null ? schedule.lastRunAt : Instant.EPOCH;
                if (timing.isFlushDue(lastRun, lastRun, now)) {
                    generateAndStore(schedule, lastRun, now);
                    scheduleStore.updateLastRunAt(schedule.id, now);
                }
            } catch (Exception e) {
                LOG.warnf("Compliance report generation failed for schedule %s: %s",
                        schedule.id, e.getMessage());
            }
        }
    }

    @Scheduled(every = "6h", identity = "compliance-retention-purge")
    void purgeExpired() {
        if (retentionDays.isEmpty()) {
            return;
        }
        Instant cutoff = Instant.now().minus(java.time.Duration.ofDays(retentionDays.get()));
        java.util.List<io.casehub.qhorus.compliance.storage.ComplianceReportRecord> expired =
                storageService.findOlderThan(cutoff);
        int purged = 0;
        for (var record : expired) {
            try {
                LOG.infof("Purging compliance report %s (type=%s, tenant=%s, generated=%s)",
                          record.id, record.reportType, record.tenancyId, record.generatedAt);
                storageService.delete(record.id);
                purged++;
            } catch (Exception e) {
                LOG.warnf(e, "Failed to purge compliance report %s", record.id);
            }
        }
        if (purged > 0) {
            LOG.infof("Compliance retention purge complete: %d reports purged (cutoff=%s)", purged, cutoff);
        }
    }


    private void generateAndStore(ComplianceReportSchedule schedule, Instant from, Instant now) {
        Object report = switch (schedule.reportType) {
            case OBLIGATION -> obligationService.generate(
                    schedule.channelId, from, now, null, schedule.tenancyId);
            case VIOLATION -> {
                if (schedule.channelId == null) {
                    throw new IllegalStateException("VIOLATION schedule requires channelId");
                }
                yield violationService.generate(schedule.channelId, from, now, schedule.tenancyId);
            }
            case JUDGMENT_FULFILLMENT -> judgmentFulfillmentService.generate(
                    from, now, null, null, schedule.tenancyId);
            default -> throw new IllegalStateException(
                    "Scheduled generation not supported for " + schedule.reportType);
        };

        ComplianceReportRecord record = storageService.store(
                schedule.reportType, report, schedule.format, schedule.id, schedule.tenancyId);

        generatedEvent.fireAsync(new ComplianceReportGeneratedEvent(
                record.id, schedule.reportType, schedule.tenancyId,
                record.artefactId, now, schedule.id,
                "system:compliance-scheduler", Map.of()));
    }
}
