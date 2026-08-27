package io.casehub.qhorus.compliance.model;

import io.casehub.qhorus.api.spi.compliance.CompliancePosture;
import io.casehub.qhorus.api.spi.compliance.PostureEntry;
import io.casehub.qhorus.api.spi.compliance.PostureStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReportModelTest {

    @Test
    void compliancePosture_emptyConstant_hasNoEntries() {
        assertThat(CompliancePosture.EMPTY.entries()).isEmpty();
    }

    @Test
    void postureEntry_recordConstruction() {
        var entry = new PostureEntry("auth", PostureStatus.COMPLIANT, "OK", "evidence-1", Instant.now());
        assertThat(entry.category()).isEqualTo("auth");
        assertThat(entry.status()).isEqualTo(PostureStatus.COMPLIANT);
    }

    @Test
    void attributionReport_recordConstruction() {
        var node = new AttributionNode(
                "e1", "ch1", "channel-a", "COMMAND", "actor-1",
                "2026-08-27T10:00:00Z", "do X", null, 0,
                0.85, "SOUND", "algo-v1", 0.9, "justified");
        var edge = new AttributionEdge("e1", "e2", "CAUSED_BY", 150L);
        var report = new AttributionReport(
                "corr-1", "e1", 1, List.of("channel-a"), 500L, "FULFILLED",
                List.of(node), List.of(edge), "abc123", Instant.now(), 1);

        assertThat(report.correlationId()).isEqualTo("corr-1");
        assertThat(report.nodes()).hasSize(1);
        assertThat(report.nodes().getFirst().algorithmRef()).isEqualTo("algo-v1");
        assertThat(report.edges()).hasSize(1);
    }

    @Test
    void obligationReport_recordConstruction_withPosture() {
        var channelSummary = new ChannelObligationSummary(
                UUID.randomUUID(), "ch-1", 10, 7, 1, 1, 1, 0, 0, 0.7);
        var agentSummary = new AgentObligationSummary(
                "agent-1", 5, 4, 1, 0, 0, 0, 0, 0.8, 0.9);
        var report = new ObligationReport(
                Instant.now().minusSeconds(3600), Instant.now(),
                List.of(channelSummary), List.of(agentSummary),
                10, 7, 1, 1, 1, 0, 0, 0.7,
                CompliancePosture.EMPTY, "merkle-root", Instant.now(), 1);

        assertThat(report.channels()).hasSize(1);
        assertThat(report.agents()).hasSize(1);
        assertThat(report.posture()).isEqualTo(CompliancePosture.EMPTY);
    }

    @Test
    void trustHistoryReport_emptyTrajectory() {
        var trajectory = new ActorTrustTrajectory("actor-1", 0.85, List.of(), List.of());
        var report = new TrustHistoryReport(
                Instant.now().minusSeconds(3600), Instant.now(),
                List.of(trajectory), null, Instant.now(), 1);

        assertThat(report.actors()).hasSize(1);
        assertThat(report.actors().getFirst().trajectory()).isEmpty();
    }

    @Test
    void violationReport_recordConstruction() {
        var entry = new ViolationEntry(
                Instant.now(), "agent-1", "COMMAND", "BLOCKING",
                List.of("TYPE_POLICY"), List.of("COMMAND not allowed"), "blocked",
                UUID.randomUUID());
        var report = new ViolationReport(
                Instant.now().minusSeconds(3600), Instant.now(),
                UUID.randomUUID(), "ch-1",
                List.of(entry), 1, 0, 0,
                Map.of("TYPE_POLICY", 1), "merkle-root", Instant.now(), 1);

        assertThat(report.violations()).hasSize(1);
        assertThat(report.totalBlocked()).isEqualTo(1);
    }

    @Test
    void provenanceReport_recordConstruction() {
        var report = new ProvenanceReport(
                "corr-1", Map.of("@context", Map.of("prov", "http://www.w3.org/ns/prov#")),
                Instant.now(), 1);

        assertThat(report.correlationId()).isEqualTo("corr-1");
        assertThat(report.provJsonLd()).containsKey("@context");
    }

    @Test
    void reportType_allValues() {
        assertThat(ReportType.values()).hasSize(5);
        assertThat(ReportType.valueOf("ATTRIBUTION")).isEqualTo(ReportType.ATTRIBUTION);
    }

    @Test
    void reportFormat_allValues() {
        assertThat(ReportFormat.values()).hasSize(3);
    }
}
