package io.casehub.qhorus.compliance.report;

import io.casehub.ledger.api.model.AttestationVerdict;
import io.casehub.ledger.api.model.LedgerAttestation;
import io.casehub.ledger.api.spi.LedgerEntryRepository;
import io.casehub.ledger.runtime.service.ComplianceReport;
import io.casehub.ledger.runtime.service.DecisionRecord;
import io.casehub.ledger.runtime.service.LedgerComplianceReportService;
import io.casehub.ledger.runtime.service.LedgerVerificationService;
import io.casehub.ledger.runtime.service.TrustGateService;
import io.casehub.qhorus.compliance.model.AttributionReport;
import io.casehub.qhorus.runtime.ledger.CausalGraphService;
import io.casehub.qhorus.runtime.ledger.CausalGraphService.CausalGraph;
import io.casehub.qhorus.runtime.ledger.CausalGraphService.GraphEdge;
import io.casehub.qhorus.runtime.ledger.CausalGraphService.GraphNode;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.OptionalDouble;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttributionReportServiceTest {

    static final String TENANCY = "test-tenant";
    static final String CORR_ID = "corr-" + UUID.randomUUID();

    final UUID entryId1 = UUID.randomUUID();
    final UUID entryId2 = UUID.randomUUID();
    final UUID channelId1 = UUID.randomUUID();
    final UUID channelId2 = UUID.randomUUID();

    @Mock CausalGraphService causalGraphService;
    @Mock TrustGateService trustGateService;
    @Mock Instance<TrustGateService> trustGateServiceInstance;
    @Mock LedgerEntryRepository ledgerEntryRepository;
    @Mock LedgerVerificationService verificationService;
    @Mock Instance<LedgerVerificationService> verificationServiceInstance;
    @Mock LedgerComplianceReportService complianceReportService;
    @Mock Instance<LedgerComplianceReportService> complianceReportServiceInstance;

    AttributionReportService service;

    @BeforeEach
    void setUp() {
        service = new AttributionReportService();
        service.causalGraphService = causalGraphService;
        service.trustGateServiceInstance = trustGateServiceInstance;
        service.ledgerEntryRepository = ledgerEntryRepository;
        service.verificationServiceInstance = verificationServiceInstance;
        service.complianceReportServiceInstance = complianceReportServiceInstance;
    }

    @Test
    void generate_buildsGraphAndEnrichesNodes() {
        CausalGraph graph = twoNodeGraph();
        when(causalGraphService.buildGraph(CORR_ID, 200, TENANCY)).thenReturn(graph);

        when(trustGateServiceInstance.isResolvable()).thenReturn(true);
        when(trustGateServiceInstance.get()).thenReturn(trustGateService);
        when(trustGateService.currentScore("agent-alpha")).thenReturn(OptionalDouble.of(0.85));
        when(trustGateService.currentScore("agent-beta")).thenReturn(OptionalDouble.of(0.72));

        LedgerAttestation attestation = new LedgerAttestation();
        attestation.verdict = AttestationVerdict.SOUND;
        attestation.confidence = 0.7;
        when(ledgerEntryRepository.findAttestationsByEntryId(entryId1, TENANCY))
                .thenReturn(List.of(attestation));
        when(ledgerEntryRepository.findAttestationsByEntryId(entryId2, TENANCY))
                .thenReturn(List.of());

        when(verificationServiceInstance.isResolvable()).thenReturn(false);
        when(complianceReportServiceInstance.isResolvable()).thenReturn(false);

        AttributionReport report = service.generate(CORR_ID, 200, TENANCY);

        assertThat(report.correlationId()).isEqualTo(CORR_ID);
        assertThat(report.nodes()).hasSize(2);
        assertThat(report.edges()).hasSize(1);
        assertThat(report.outcome()).isEqualTo("FULFILLED");
        assertThat(report.channelCount()).isEqualTo(2);
        assertThat(report.schemaVersion()).isEqualTo(1);

        var node1 = report.nodes().stream()
                .filter(n -> n.entryId().equals(entryId1.toString())).findFirst().orElseThrow();
        assertThat(node1.trustScoreAtTime()).isEqualTo(0.85);
        assertThat(node1.attestationVerdict()).isEqualTo("SOUND");

        var node2 = report.nodes().stream()
                .filter(n -> n.entryId().equals(entryId2.toString())).findFirst().orElseThrow();
        assertThat(node2.trustScoreAtTime()).isEqualTo(0.72);
        assertThat(node2.attestationVerdict()).isNull();
    }

    @Test
    void generate_enrichesWithComplianceSupplement() {
        CausalGraph graph = twoNodeGraph();
        when(causalGraphService.buildGraph(CORR_ID, 200, TENANCY)).thenReturn(graph);

        when(trustGateServiceInstance.isResolvable()).thenReturn(false);
        when(verificationServiceInstance.isResolvable()).thenReturn(false);
        when(ledgerEntryRepository.findAttestationsByEntryId(any(UUID.class), eq(TENANCY)))
                .thenReturn(List.of());

        when(complianceReportServiceInstance.isResolvable()).thenReturn(true);
        when(complianceReportServiceInstance.get()).thenReturn(complianceReportService);

        DecisionRecord dr = new DecisionRecord(
                entryId1, Instant.parse("2026-08-27T10:00:00Z"),
                "classification-v3.2", 0.91, null, true, null, null);
        ComplianceReport compReport = new ComplianceReport(
                null, channelId1,
                Instant.parse("2026-08-27T10:00:00Z"),
                Instant.parse("2026-08-27T10:05:00Z"),
                1, List.of(dr), "merkle-ch1");
        when(complianceReportService.reportForSubject(eq(channelId1), any(), any(), eq(TENANCY)))
                .thenReturn(compReport);
        when(complianceReportService.reportForSubject(eq(channelId2), any(), any(), eq(TENANCY)))
                .thenReturn(new ComplianceReport(null, channelId2,
                        Instant.parse("2026-08-27T10:00:00Z"),
                        Instant.parse("2026-08-27T10:05:00Z"),
                        0, List.of(), null));

        AttributionReport report = service.generate(CORR_ID, 200, TENANCY);

        var enriched = report.nodes().stream()
                .filter(n -> n.entryId().equals(entryId1.toString())).findFirst().orElseThrow();
        assertThat(enriched.algorithmRef()).isEqualTo("classification-v3.2");
        assertThat(enriched.confidenceScore()).isEqualTo(0.91);

        var unenriched = report.nodes().stream()
                .filter(n -> n.entryId().equals(entryId2.toString())).findFirst().orElseThrow();
        assertThat(unenriched.algorithmRef()).isNull();
        assertThat(unenriched.confidenceScore()).isNull();
    }

    @Test
    void generate_compositesMerkleRoot() {
        CausalGraph graph = twoNodeGraph();
        when(causalGraphService.buildGraph(CORR_ID, 200, TENANCY)).thenReturn(graph);

        when(trustGateServiceInstance.isResolvable()).thenReturn(false);
        when(complianceReportServiceInstance.isResolvable()).thenReturn(false);
        when(ledgerEntryRepository.findAttestationsByEntryId(any(UUID.class), eq(TENANCY)))
                .thenReturn(List.of());

        when(verificationServiceInstance.isResolvable()).thenReturn(true);
        when(verificationServiceInstance.get()).thenReturn(verificationService);
        when(verificationService.treeRoot(channelId1, TENANCY)).thenReturn("root-hash-1");
        when(verificationService.treeRoot(channelId2, TENANCY)).thenReturn("root-hash-2");

        AttributionReport report = service.generate(CORR_ID, 200, TENANCY);

        assertThat(report.merkleRoot()).isNotNull();
        assertThat(report.merkleRoot()).contains(channelId1 + "=root-hash-1");
        assertThat(report.merkleRoot()).contains(channelId2 + "=root-hash-2");
        assertThat(report.merkleRoot()).contains(";");
    }

    @Test
    void generate_unknownCorrelation_returnsEmptyGraph() {
        CausalGraph emptyGraph = new CausalGraph(
                CORR_ID, null, 0, List.of(), null, "OPEN",
                false, List.of(), List.of());
        when(causalGraphService.buildGraph(CORR_ID, 200, TENANCY)).thenReturn(emptyGraph);

        AttributionReport report = service.generate(CORR_ID, 200, TENANCY);

        assertThat(report.correlationId()).isEqualTo(CORR_ID);
        assertThat(report.nodes()).isEmpty();
        assertThat(report.edges()).isEmpty();
        assertThat(report.outcome()).isEqualTo("OPEN");
        assertThat(report.merkleRoot()).isNull();
        assertThat(report.generatedAt()).isNotNull();
    }

    @Test
    void generate_trustScoreFallback_currentScoreWhenSnapshotUnavailable() {
        CausalGraph graph = twoNodeGraph();
        when(causalGraphService.buildGraph(CORR_ID, 200, TENANCY)).thenReturn(graph);

        when(trustGateServiceInstance.isResolvable()).thenReturn(true);
        when(trustGateServiceInstance.get()).thenReturn(trustGateService);
        when(trustGateService.currentScore("agent-alpha")).thenReturn(OptionalDouble.of(0.85));
        when(trustGateService.currentScore("agent-beta")).thenReturn(OptionalDouble.empty());

        when(ledgerEntryRepository.findAttestationsByEntryId(any(UUID.class), eq(TENANCY)))
                .thenReturn(List.of());
        when(verificationServiceInstance.isResolvable()).thenReturn(false);
        when(complianceReportServiceInstance.isResolvable()).thenReturn(false);

        AttributionReport report = service.generate(CORR_ID, 200, TENANCY);

        var node1 = report.nodes().stream()
                .filter(n -> n.entryId().equals(entryId1.toString())).findFirst().orElseThrow();
        assertThat(node1.trustScoreAtTime()).isEqualTo(0.85);

        var node2 = report.nodes().stream()
                .filter(n -> n.entryId().equals(entryId2.toString())).findFirst().orElseThrow();
        assertThat(node2.trustScoreAtTime()).isNull();
    }

    @Test
    void generate_merkleRoot_skipsChannelWithNoEntries() {
        CausalGraph graph = twoNodeGraph();
        when(causalGraphService.buildGraph(CORR_ID, 200, TENANCY)).thenReturn(graph);

        when(trustGateServiceInstance.isResolvable()).thenReturn(false);
        when(complianceReportServiceInstance.isResolvable()).thenReturn(false);
        when(ledgerEntryRepository.findAttestationsByEntryId(any(UUID.class), eq(TENANCY)))
                .thenReturn(List.of());

        when(verificationServiceInstance.isResolvable()).thenReturn(true);
        when(verificationServiceInstance.get()).thenReturn(verificationService);
        when(verificationService.treeRoot(channelId1, TENANCY)).thenReturn("root-hash-1");
        when(verificationService.treeRoot(channelId2, TENANCY))
                .thenThrow(new IllegalStateException("No entries for subject"));

        AttributionReport report = service.generate(CORR_ID, 200, TENANCY);

        assertThat(report.merkleRoot()).isEqualTo(channelId1 + "=root-hash-1");
        assertThat(report.merkleRoot()).doesNotContain(";");
    }

    private CausalGraph twoNodeGraph() {
        GraphNode node1 = new GraphNode(
                entryId1.toString(), channelId1.toString(), "command-channel",
                "COMMAND", "agent-alpha", "2026-08-27T10:00:00Z",
                "analyze data", null, 0);
        GraphNode node2 = new GraphNode(
                entryId2.toString(), channelId2.toString(), "response-channel",
                "DONE", "agent-beta", "2026-08-27T10:05:00Z",
                "analysis complete", entryId1.toString(), 1);
        GraphEdge edge = new GraphEdge(
                entryId1.toString(), entryId2.toString(), "CAUSED_BY", 300_000L);

        return new CausalGraph(
                CORR_ID, entryId1.toString(), 2,
                List.of("command-channel", "response-channel"),
                300_000L, "FULFILLED", false,
                List.of(node1, node2), List.of(edge));
    }
}
