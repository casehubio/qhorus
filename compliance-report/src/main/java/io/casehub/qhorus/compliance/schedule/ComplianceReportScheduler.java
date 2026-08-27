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
