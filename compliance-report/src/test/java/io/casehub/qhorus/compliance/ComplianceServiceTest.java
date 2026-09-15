package io.casehub.qhorus.compliance;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.qhorus.api.compliance.ComplianceReportRecordView;
import io.casehub.qhorus.api.compliance.ComplianceScheduleInput;
import io.casehub.qhorus.api.compliance.ComplianceScheduleUpdateInput;
import io.casehub.qhorus.api.compliance.ComplianceScheduleView;
import io.casehub.qhorus.api.compliance.report.AttributionEdge;
import io.casehub.qhorus.api.compliance.report.AttributionNode;
import io.casehub.qhorus.api.compliance.report.AttributionReport;
import io.casehub.qhorus.api.compliance.report.ObligationReport;
import io.casehub.qhorus.api.compliance.report.ReportFormat;
import io.casehub.qhorus.api.compliance.report.ReportType;
import io.casehub.qhorus.api.compliance.report.ViolationReport;
import io.casehub.qhorus.api.spi.compliance.CompliancePosture;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplianceServiceTest {

    static final String TENANCY = "test-tenant";
    static final String CORRELATION_ID = "corr-123";

    @Mock AttributionReportService attributionService;
    @Mock ObligationReportService obligationService;
    @Mock ViolationReportService violationService;
    @Mock TrustHistoryReportService trustHistoryService;
    @Mock ProvenanceReportService provenanceService;
    @Mock ComplianceReportRecordStore recordStore;
    @Mock CurrentPrincipal currentPrincipal;
    @Mock JudgmentAttributionReportService judgmentAttributionService;
    @Mock JudgmentFulfillmentReportService judgmentFulfillmentService;
    @Mock PropertyVerificationService propertyVerificationService;
    @Mock ComplianceReportScheduleStore scheduleStore;
    @Mock ComplianceReportStorageService storageService;

    ComplianceService service;

    @BeforeEach
    void setUp() {
        service = new ComplianceService(
                attributionService, obligationService, violationService,
                trustHistoryService, provenanceService, recordStore,
                currentPrincipal, judgmentAttributionService,
                judgmentFulfillmentService, propertyVerificationService,
                scheduleStore, storageService);
    }

    @Test
    void complianceAttribution_returnsReport() {
        when(currentPrincipal.tenancyId()).thenReturn(TENANCY);
        AttributionReport report = new AttributionReport(
                CORRELATION_ID, "entry-1", 2, List.of("ch-a", "ch-b"), 500L, "DONE",
                List.of(node("entry-1"), node("entry-2")),
                List.of(new AttributionEdge("entry-2", "entry-1", "CAUSED_BY", 200L)),
                "merkle-root", Instant.now(), 1);
        when(attributionService.generate(eq(CORRELATION_ID), eq(100), eq(TENANCY))).thenReturn(report);

        AttributionReport result = service.complianceAttribution(CORRELATION_ID, null);

        assertThat(result.correlationId()).isEqualTo(CORRELATION_ID);
        assertThat(result.outcome()).isEqualTo("DONE");
        assertThat(result.nodes()).hasSize(2);
        assertThat(result.edges()).hasSize(1);
        assertThat(result.channelCount()).isEqualTo(2);
    }

    @Test
    void complianceAttribution_usesExplicitLimit() {
        when(currentPrincipal.tenancyId()).thenReturn(TENANCY);
        when(attributionService.generate(eq(CORRELATION_ID), eq(50), eq(TENANCY)))
                .thenReturn(emptyAttribution());

        service.complianceAttribution(CORRELATION_ID, 50);

        verify(attributionService).generate(CORRELATION_ID, 50, TENANCY);
    }

    @Test
    void complianceObligations_defaultsToLast30Days() {
        when(currentPrincipal.tenancyId()).thenReturn(TENANCY);
        when(obligationService.generate(any(), any(), any(), any(), eq(TENANCY)))
                .thenReturn(emptyObligation());

        ObligationReport result = service.complianceObligations(null, null, null);

        assertThat(result).isNotNull();
        assertThat(result.channels()).isEmpty();
        verify(obligationService).generate(eq(null), any(Instant.class), any(Instant.class), eq(null), eq(TENANCY));
    }

    @Test
    void complianceObligations_parsesChannelIdAndTimeWindow() {
        when(currentPrincipal.tenancyId()).thenReturn(TENANCY);
        UUID channelId = UUID.randomUUID();
        String from = "2026-08-01T00:00:00Z";
        String to = "2026-08-31T23:59:59Z";

        when(obligationService.generate(eq(channelId), eq(Instant.parse(from)), eq(Instant.parse(to)), eq(null), eq(TENANCY)))
                .thenReturn(emptyObligation());

        service.complianceObligations(channelId.toString(), from, to);

        verify(obligationService).generate(channelId, Instant.parse(from), Instant.parse(to), null, TENANCY);
    }

    @Test
    void complianceViolations_returnsReport() {
        when(currentPrincipal.tenancyId()).thenReturn(TENANCY);
        UUID channelId = UUID.randomUUID();
        ViolationReport report = new ViolationReport(
                Instant.now().minusSeconds(3600), Instant.now(),
                channelId, "test-channel", List.of(),
                5, 2, 0, null, null, Instant.now(), 1);
        when(violationService.generate(eq(channelId), any(), any(), eq(TENANCY))).thenReturn(report);

        ViolationReport result = service.complianceViolations(channelId.toString(), null, null);

        assertThat(result.channelId()).isEqualTo(channelId);
        assertThat(result.totalBlocked()).isEqualTo(5);
        assertThat(result.totalAdvisory()).isEqualTo(2);
    }

    @Test
    void complianceReports_listsByType() {
        when(currentPrincipal.tenancyId()).thenReturn(TENANCY);
        ComplianceReportRecord record = new ComplianceReportRecord();
        record.id = UUID.randomUUID();
        record.reportType = ReportType.OBLIGATION;
        record.tenancyId = TENANCY;
        record.generatedAt = Instant.now();
        record.artefactId = UUID.randomUUID();
        record.format = ReportFormat.JSON;
        record.schemaVersion = 1;

        when(recordStore.findByType(eq(ReportType.OBLIGATION), eq(TENANCY), anyInt()))
                .thenReturn(List.of(record));

        List<ComplianceReportRecordView> results = service.complianceReports("OBLIGATION", null);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().reportType()).isEqualTo(ReportType.OBLIGATION);
    }

    @Test
    void complianceReports_listsByTimeRangeWhenNoType() {
        when(currentPrincipal.tenancyId()).thenReturn(TENANCY);
        when(recordStore.findByTimeRange(any(), any(), eq(TENANCY), anyInt())).thenReturn(List.of());

        List<ComplianceReportRecordView> results = service.complianceReports(null, null);

        assertThat(results).isEmpty();
        verify(recordStore).findByTimeRange(any(Instant.class), any(Instant.class), eq(TENANCY), eq(20));
    }

    @Test
    void createComplianceSchedule_persistsAndReturnsView() {
        when(currentPrincipal.tenancyId()).thenReturn(TENANCY);
        ComplianceScheduleInput input = new ComplianceScheduleInput(
                ReportType.OBLIGATION, null, "{\"type\":\"interval\",\"period\":\"PT24H\"}", ReportFormat.JSON);

        ComplianceReportSchedule saved = scheduleEntity();
        when(scheduleStore.save(any())).thenReturn(saved);

        ComplianceScheduleView result = service.createComplianceSchedule(input);

        assertThat(result.reportType()).isEqualTo(ReportType.OBLIGATION);
        assertThat(result.tenancyId()).isEqualTo(TENANCY);
        assertThat(result.enabled()).isTrue();

        ArgumentCaptor<ComplianceReportSchedule> captor = ArgumentCaptor.forClass(ComplianceReportSchedule.class);
        verify(scheduleStore).save(captor.capture());
        assertThat(captor.getValue().tenancyId).isEqualTo(TENANCY);
        assertThat(captor.getValue().enabled).isTrue();
    }

    @Test
    void updateComplianceSchedule_togglesEnabled() {
        ComplianceReportSchedule existing = scheduleEntity();
        existing.enabled = true;
        when(scheduleStore.findById(existing.id)).thenReturn(Optional.of(existing));
        when(scheduleStore.save(any())).thenReturn(existing);

        ComplianceScheduleUpdateInput input = new ComplianceScheduleUpdateInput(existing.id, false, null);
        service.updateComplianceSchedule(input);

        assertThat(existing.enabled).isFalse();
        verify(scheduleStore).save(existing);
    }

    @Test
    void updateComplianceSchedule_updatesScheduleJson() {
        ComplianceReportSchedule existing = scheduleEntity();
        when(scheduleStore.findById(existing.id)).thenReturn(Optional.of(existing));
        when(scheduleStore.save(any())).thenReturn(existing);

        String newJson = "{\"type\":\"interval\",\"period\":\"PT48H\"}";
        ComplianceScheduleUpdateInput input = new ComplianceScheduleUpdateInput(existing.id, null, newJson);
        service.updateComplianceSchedule(input);

        assertThat(existing.scheduleJson).isEqualTo(newJson);
    }

    @Test
    void deleteComplianceSchedule_callsStore() {
        UUID id = UUID.randomUUID();
        boolean result = service.deleteComplianceSchedule(id);

        assertThat(result).isTrue();
        verify(scheduleStore).delete(id);
    }

    @Test
    void deleteComplianceReport_callsStorageService() {
        UUID id = UUID.randomUUID();
        boolean result = service.deleteComplianceReport(id);

        assertThat(result).isTrue();
        verify(storageService).delete(id);
    }

    private AttributionNode node(String entryId) {
        return new AttributionNode(entryId, UUID.randomUUID().toString(), "ch-a",
                "COMMAND", "agent-1", Instant.now().toString(),
                "content", null, 0, 0.9, "SOUND", null, 0.8, null);
    }

    private AttributionReport emptyAttribution() {
        return new AttributionReport(CORRELATION_ID, null, 0, List.of(), null, null,
                List.of(), List.of(), null, Instant.now(), 1);
    }

    private ObligationReport emptyObligation() {
        return new ObligationReport(
                Instant.now().minusSeconds(3600), Instant.now(),
                List.of(), List.of(), 0, 0, 0, 0, 0, 0, 0, 0.0,
                CompliancePosture.EMPTY, null, Instant.now(), 1);
    }

    private ComplianceReportSchedule scheduleEntity() {
        ComplianceReportSchedule s = new ComplianceReportSchedule();
        s.id = UUID.randomUUID();
        s.reportType = ReportType.OBLIGATION;
        s.scheduleJson = "{\"type\":\"interval\",\"period\":\"PT24H\"}";
        s.format = ReportFormat.JSON;
        s.tenancyId = TENANCY;
        s.enabled = true;
        s.createdAt = Instant.now();
        return s;
    }
}
