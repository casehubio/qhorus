package io.casehub.qhorus.compliance;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.qhorus.api.compliance.ComplianceReportRecordView;
import io.casehub.qhorus.api.compliance.ComplianceScheduleInput;
import io.casehub.qhorus.api.compliance.ComplianceScheduleUpdateInput;
import io.casehub.qhorus.api.compliance.ComplianceScheduleView;
import io.casehub.qhorus.api.compliance.report.AttributionReport;
import io.casehub.qhorus.api.compliance.report.JudgmentAttributionReport;
import io.casehub.qhorus.api.compliance.report.JudgmentFulfillmentReport;
import io.casehub.qhorus.api.compliance.report.ObligationReport;
import io.casehub.qhorus.api.compliance.report.PropertyVerificationReport;
import io.casehub.qhorus.api.compliance.report.ProvenanceReport;
import io.casehub.qhorus.api.compliance.report.ReportType;
import io.casehub.qhorus.api.compliance.report.TrustHistoryReport;
import io.casehub.qhorus.api.compliance.report.ViolationReport;
import io.casehub.qhorus.api.spi.compliance.ComplianceApi;
import io.casehub.qhorus.compliance.report.AttributionReportService;
import io.casehub.qhorus.compliance.report.JudgmentAttributionReportService;
import io.casehub.qhorus.compliance.report.JudgmentFulfillmentReportService;
import io.casehub.qhorus.compliance.report.ObligationReportService;
import io.casehub.qhorus.compliance.report.ProvenanceReportService;
import io.casehub.qhorus.compliance.report.TrustHistoryReportService;
import io.casehub.qhorus.compliance.report.ViolationReportService;
import io.casehub.qhorus.compliance.schedule.ComplianceReportSchedule;
import io.casehub.qhorus.compliance.schedule.ComplianceReportScheduleStore;
import io.casehub.qhorus.compliance.storage.ComplianceReportRecord;
import io.casehub.qhorus.compliance.storage.ComplianceReportRecordStore;
import io.casehub.qhorus.compliance.storage.ComplianceReportStorageService;
import io.casehub.qhorus.compliance.verification.PropertyVerificationService;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class ComplianceService implements ComplianceApi {

    private final AttributionReportService attributionService;
    private final ObligationReportService obligationService;
    private final ViolationReportService violationService;
    private final TrustHistoryReportService trustHistoryService;
    private final ProvenanceReportService provenanceService;
    private final ComplianceReportRecordStore recordStore;
    private final CurrentPrincipal currentPrincipal;
    private final JudgmentAttributionReportService judgmentAttributionService;
    private final JudgmentFulfillmentReportService judgmentFulfillmentService;
    private final PropertyVerificationService propertyVerificationService;
    private final ComplianceReportScheduleStore scheduleStore;
    private final ComplianceReportStorageService storageService;

    public ComplianceService(AttributionReportService attributionService,
                             ObligationReportService obligationService,
                             ViolationReportService violationService,
                             TrustHistoryReportService trustHistoryService,
                             ProvenanceReportService provenanceService,
                             ComplianceReportRecordStore recordStore,
                             CurrentPrincipal currentPrincipal,
                             JudgmentAttributionReportService judgmentAttributionService,
                             JudgmentFulfillmentReportService judgmentFulfillmentService,
                             PropertyVerificationService propertyVerificationService,
                             ComplianceReportScheduleStore scheduleStore,
                             ComplianceReportStorageService storageService) {
        this.attributionService = attributionService;
        this.obligationService = obligationService;
        this.violationService = violationService;
        this.trustHistoryService = trustHistoryService;
        this.provenanceService = provenanceService;
        this.recordStore = recordStore;
        this.currentPrincipal = currentPrincipal;
        this.judgmentAttributionService = judgmentAttributionService;
        this.judgmentFulfillmentService = judgmentFulfillmentService;
        this.propertyVerificationService = propertyVerificationService;
        this.scheduleStore = scheduleStore;
        this.storageService = storageService;
    }

    @Override
    public AttributionReport complianceAttribution(String correlationId, Integer limit) {
        int depth = limit != null ? limit : 100;
        return attributionService.generate(correlationId, depth, currentPrincipal.tenancyId());
    }

    @Override
    public ObligationReport complianceObligations(String channelId, String from, String to) {
        UUID chId = channelId != null ? UUID.fromString(channelId) : null;
        Instant f = from != null ? Instant.parse(from) : Instant.now().minus(30, ChronoUnit.DAYS);
        Instant t = to != null ? Instant.parse(to) : Instant.now();
        return obligationService.generate(chId, f, t, null, currentPrincipal.tenancyId());
    }

    @Override
    public ViolationReport complianceViolations(String channelId, String from, String to) {
        UUID chId = channelId != null ? UUID.fromString(channelId) : null;
        Instant f = from != null ? Instant.parse(from) : Instant.now().minus(30, ChronoUnit.DAYS);
        Instant t = to != null ? Instant.parse(to) : Instant.now();
        return violationService.generate(chId, f, t, currentPrincipal.tenancyId());
    }

    @Override
    public TrustHistoryReport complianceTrustHistory(String actorId, String from, String to) {
        Instant f = from != null ? Instant.parse(from) : Instant.now().minus(30, ChronoUnit.DAYS);
        Instant t = to != null ? Instant.parse(to) : Instant.now();
        return trustHistoryService.generate(actorId, f, t, currentPrincipal.tenancyId());
    }

    @Override
    public ProvenanceReport complianceProvenance(String correlationId, Integer limit) {
        int depth = limit != null ? limit : 100;
        return provenanceService.generate(correlationId, depth, currentPrincipal.tenancyId());
    }

    @Override
    public List<ComplianceReportRecordView> complianceReports(String reportType, Integer limit) {
        int max = limit != null ? limit : 20;
        String tenancyId = currentPrincipal.tenancyId();
        if (reportType != null) {
            return recordStore.findByType(ReportType.valueOf(reportType), tenancyId, max)
                    .stream().map(ComplianceService::toRecordView).toList();
        }
        return recordStore.findByTimeRange(
                        Instant.now().minus(90, ChronoUnit.DAYS), Instant.now(), tenancyId, max)
                .stream().map(ComplianceService::toRecordView).toList();
    }

    @Override
    public JudgmentAttributionReport complianceJudgmentAttribution(String judgmentId, Integer limit) {
        int depth = limit != null ? limit : 200;
        return judgmentAttributionService.generate(UUID.fromString(judgmentId), depth, currentPrincipal.tenancyId());
    }

    @Override
    public JudgmentFulfillmentReport complianceJudgmentFulfillment(String from, String to, String judgmentType, String actorId) {
        Instant f = from != null ? Instant.parse(from) : Instant.now().minus(30, ChronoUnit.DAYS);
        Instant t = to != null ? Instant.parse(to) : Instant.now();
        return judgmentFulfillmentService.generate(f, t, judgmentType, actorId, currentPrincipal.tenancyId());
    }

    @Override
    public PropertyVerificationReport compliancePropertyVerification(String from, String to) {
        Instant f = from != null ? Instant.parse(from) : Instant.now().minus(30, ChronoUnit.DAYS);
        Instant t = to != null ? Instant.parse(to) : Instant.now();
        return propertyVerificationService.verify(currentPrincipal.tenancyId(), f, t);
    }

    @Override
    public ComplianceScheduleView createComplianceSchedule(ComplianceScheduleInput input) {
        ComplianceReportSchedule schedule = new ComplianceReportSchedule();
        schedule.reportType = input.reportType();
        schedule.channelId = input.channelId();
        schedule.scheduleJson = input.scheduleJson();
        schedule.format = input.format();
        schedule.tenancyId = currentPrincipal.tenancyId();
        schedule.enabled = true;
        return toScheduleView(scheduleStore.save(schedule));
    }

    @Override
    public ComplianceScheduleView updateComplianceSchedule(ComplianceScheduleUpdateInput input) {
        ComplianceReportSchedule schedule = scheduleStore.findById(input.id())
                .orElseThrow(() -> new IllegalArgumentException("Schedule not found: " + input.id()));
        if (input.enabled() != null) schedule.enabled = input.enabled();
        if (input.scheduleJson() != null) schedule.scheduleJson = input.scheduleJson();
        return toScheduleView(scheduleStore.save(schedule));
    }

    @Override
    public boolean deleteComplianceSchedule(UUID id) {
        scheduleStore.delete(id);
        return true;
    }

    @Override
    public boolean deleteComplianceReport(UUID id) {
        storageService.delete(id);
        return true;
    }

    private static ComplianceReportRecordView toRecordView(ComplianceReportRecord record) {
        return new ComplianceReportRecordView(
                record.id,
                record.reportType,
                record.tenancyId,
                record.generatedAt,
                record.scheduleId,
                record.artefactId,
                record.format,
                record.schemaVersion
        );
    }

    private static ComplianceScheduleView toScheduleView(ComplianceReportSchedule schedule) {
        return new ComplianceScheduleView(
                schedule.id,
                schedule.reportType,
                schedule.channelId,
                schedule.scheduleJson,
                schedule.format,
                schedule.tenancyId,
                schedule.enabled,
                schedule.lastRunAt,
                schedule.createdAt
        );
    }
}
