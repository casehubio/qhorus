package io.casehub.qhorus.compliance.graphql;

import io.casehub.platform.api.identity.CurrentPrincipal;
import io.casehub.qhorus.api.spi.compliance.CompliancePosture;
import io.casehub.qhorus.compliance.graphql.dto.AttributionReportType;
import io.casehub.qhorus.compliance.graphql.dto.ObligationReportType;
import io.casehub.qhorus.compliance.graphql.dto.ViolationReportType;
import io.casehub.qhorus.compliance.model.AttributionEdge;
import io.casehub.qhorus.compliance.model.AttributionNode;
import io.casehub.qhorus.compliance.model.AttributionReport;
import io.casehub.qhorus.compliance.model.ObligationReport;
import io.casehub.qhorus.compliance.model.ReportFormat;
import io.casehub.qhorus.compliance.model.ReportType;
import io.casehub.qhorus.compliance.model.ViolationReport;
import io.casehub.qhorus.compliance.report.AttributionReportService;
import io.casehub.qhorus.compliance.report.ObligationReportService;
import io.casehub.qhorus.compliance.report.ProvenanceReportService;
import io.casehub.qhorus.compliance.report.TrustHistoryReportService;
import io.casehub.qhorus.compliance.report.ViolationReportService;
import io.casehub.qhorus.compliance.storage.ComplianceReportRecord;
import io.casehub.qhorus.compliance.storage.ComplianceReportRecordStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ComplianceQueryResolverTest {

    static final String TENANCY = "test-tenant";
    static final String CORRELATION_ID = "corr-123";

    @Mock AttributionReportService attributionService;
    @Mock ObligationReportService obligationService;
    @Mock ViolationReportService violationService;
    @Mock TrustHistoryReportService trustHistoryService;
    @Mock ProvenanceReportService provenanceService;
    @Mock ComplianceReportRecordStore recordStore;
    @Mock CurrentPrincipal currentPrincipal;

    ComplianceQueryResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new ComplianceQueryResolver();
        resolver.attributionService = attributionService;
        resolver.obligationService = obligationService;
        resolver.violationService = violationService;
        resolver.trustHistoryService = trustHistoryService;
        resolver.provenanceService = provenanceService;
        resolver.recordStore = recordStore;
        resolver.currentPrincipal = currentPrincipal;

        when(currentPrincipal.tenancyId()).thenReturn(TENANCY);
    }

    @Test
    void complianceAttribution_returnsReport() {
        AttributionReport report = new AttributionReport(
                CORRELATION_ID, "entry-1", 2, List.of("ch-a", "ch-b"), 500L, "DONE",
                List.of(node("entry-1"), node("entry-2")),
                List.of(new AttributionEdge("entry-2", "entry-1", "CAUSED_BY", 200L)),
                "merkle-root", Instant.now(), 1);
        when(attributionService.generate(eq(CORRELATION_ID), eq(100), eq(TENANCY))).thenReturn(report);

        AttributionReportType result = resolver.complianceAttribution(CORRELATION_ID, null);

        assertThat(result.correlationId()).isEqualTo(CORRELATION_ID);
        assertThat(result.outcome()).isEqualTo("DONE");
        assertThat(result.nodes()).hasSize(2);
        assertThat(result.edges()).hasSize(1);
        assertThat(result.channelCount()).isEqualTo(2);
    }

    @Test
    void complianceAttribution_usesExplicitLimit() {
        when(attributionService.generate(eq(CORRELATION_ID), eq(50), eq(TENANCY)))
                .thenReturn(emptyAttribution());

        resolver.complianceAttribution(CORRELATION_ID, 50);

        verify(attributionService).generate(CORRELATION_ID, 50, TENANCY);
    }

    @Test
    void complianceObligations_defaultsToLast30Days() {
        when(obligationService.generate(any(), any(), any(), any(), eq(TENANCY)))
                .thenReturn(emptyObligation());

        ObligationReportType result = resolver.complianceObligations(null, null, null);

        assertThat(result).isNotNull();
        assertThat(result.channels()).isEmpty();
        verify(obligationService).generate(eq(null), any(Instant.class), any(Instant.class), eq(null), eq(TENANCY));
    }

    @Test
    void complianceObligations_parsesChannelIdAndTimeWindow() {
        UUID channelId = UUID.randomUUID();
        String from = "2026-08-01T00:00:00Z";
        String to = "2026-08-31T23:59:59Z";

        when(obligationService.generate(eq(channelId), eq(Instant.parse(from)), eq(Instant.parse(to)), eq(null), eq(TENANCY)))
                .thenReturn(emptyObligation());

        resolver.complianceObligations(channelId.toString(), from, to);

        verify(obligationService).generate(channelId, Instant.parse(from), Instant.parse(to), null, TENANCY);
    }

    @Test
    void complianceViolations_returnsReport() {
        UUID channelId = UUID.randomUUID();
        ViolationReport report = new ViolationReport(
                Instant.now().minusSeconds(3600), Instant.now(),
                channelId, "test-channel", List.of(),
                5, 2, 0, null, null, Instant.now(), 1);
        when(violationService.generate(eq(channelId), any(), any(), eq(TENANCY))).thenReturn(report);

        ViolationReportType result = resolver.complianceViolations(channelId.toString(), null, null);

        assertThat(result.channelId()).isEqualTo(channelId);
        assertThat(result.totalBlocked()).isEqualTo(5);
        assertThat(result.totalAdvisory()).isEqualTo(2);
    }

    @Test
    void complianceReports_listsByType() {
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

        var results = resolver.complianceReports("OBLIGATION", null);

        assertThat(results).hasSize(1);
        assertThat(results.getFirst().reportType()).isEqualTo(ReportType.OBLIGATION);
    }

    @Test
    void complianceReports_listsByTimeRangeWhenNoType() {
        when(recordStore.findByTimeRange(any(), any(), eq(TENANCY), anyInt())).thenReturn(List.of());

        var results = resolver.complianceReports(null, null);

        assertThat(results).isEmpty();
        verify(recordStore).findByTimeRange(any(Instant.class), any(Instant.class), eq(TENANCY), eq(20));
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
}
