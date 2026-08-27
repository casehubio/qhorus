package io.casehub.qhorus.compliance.format;

import io.casehub.qhorus.api.spi.compliance.CompliancePosture;
import io.casehub.qhorus.compliance.model.AgentObligationSummary;
import io.casehub.qhorus.compliance.model.AttributionEdge;
import io.casehub.qhorus.compliance.model.AttributionNode;
import io.casehub.qhorus.compliance.model.AttributionReport;
import io.casehub.qhorus.compliance.model.ChannelObligationSummary;
import io.casehub.qhorus.compliance.model.ObligationReport;
import io.casehub.qhorus.compliance.model.ReportFormat;
import io.casehub.qhorus.compliance.model.ViolationEntry;
import io.casehub.qhorus.compliance.model.ViolationReport;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CsvReportRendererTest {

    final CsvReportRenderer renderer = new CsvReportRenderer();

    @Test
    void render_attributionReport_oneRowPerNode() {
        var node = new AttributionNode(
                "e1", "ch1", "channel-a", "COMMAND", "actor-1",
                "2026-08-27T10:00:00Z", "do X", null, 0,
                0.85, "SOUND", "algo-v1", 0.9, null);
        var report = new AttributionReport(
                "corr-1", "e1", 1, List.of("channel-a"), 500L, "FULFILLED",
                List.of(node), List.of(), "root", Instant.now(), 1);

        String csv = new String(renderer.render(report), StandardCharsets.UTF_8);

        assertThat(csv).startsWith("entryId,channel,messageType,actorId,timestamp,depth,trustScore,verdict,algorithmRef,confidenceScore\n");
        assertThat(csv).contains("e1,channel-a,COMMAND,actor-1,2026-08-27T10:00:00Z,0,0.85,SOUND,algo-v1,0.9");
    }

    @Test
    void render_obligationReport_twoSections() {
        var chSummary = new ChannelObligationSummary(
                UUID.randomUUID(), "ch-1", 10, 7, 1, 1, 1, 0, 0, 0.7);
        var agSummary = new AgentObligationSummary(
                "agent-1", 5, 4, 1, 0, 0, 0, 0, 0.8, 0.9);
        var report = new ObligationReport(
                Instant.now().minusSeconds(3600), Instant.now(),
                List.of(chSummary), List.of(agSummary),
                10, 7, 1, 1, 1, 0, 0, 0.7,
                CompliancePosture.EMPTY, null, Instant.now(), 1);

        String csv = new String(renderer.render(report), StandardCharsets.UTF_8);

        assertThat(csv).contains("channelId,channelName,total,fulfilled");
        assertThat(csv).contains("actorId,total,fulfilled,failed");
        assertThat(csv).contains("ch-1,10,7");
        assertThat(csv).contains("agent-1,5,4");
    }

    @Test
    void render_handlesEmbeddedCommasAndQuotes() {
        var node = new AttributionNode(
                "e1", "ch1", "channel, with comma", "COMMAND", "actor \"special\"",
                "2026-08-27T10:00:00Z", "content", null, 0,
                null, null, null, null, null);
        var report = new AttributionReport(
                "corr-1", "e1", 1, List.of("ch"), 0L, "OPEN",
                List.of(node), List.of(), null, Instant.now(), 1);

        String csv = new String(renderer.render(report), StandardCharsets.UTF_8);

        assertThat(csv).contains("\"channel, with comma\"");
        assertThat(csv).contains("\"actor \"\"special\"\"\"");
    }

    @Test
    void render_handlesNullFields() {
        var node = new AttributionNode(
                "e1", "ch1", "channel-a", "COMMAND", "actor-1",
                null, null, null, 0,
                null, null, null, null, null);
        var report = new AttributionReport(
                "corr-1", "e1", 1, List.of("ch"), 0L, "OPEN",
                List.of(node), List.of(), null, Instant.now(), 1);

        String csv = new String(renderer.render(report), StandardCharsets.UTF_8);

        assertThat(csv).contains("e1,channel-a,COMMAND,actor-1,,0,,,,");
    }

    @Test
    void supports_csv() {
        assertThat(renderer.supports(ReportFormat.CSV)).isTrue();
        assertThat(renderer.supports(ReportFormat.JSON)).isFalse();
        assertThat(renderer.supports(ReportFormat.HTML)).isFalse();
    }

    @Test
    void escape_rfc4180() {
        assertThat(CsvReportRenderer.escape(null)).isEmpty();
        assertThat(CsvReportRenderer.escape("simple")).isEqualTo("simple");
        assertThat(CsvReportRenderer.escape("has,comma")).isEqualTo("\"has,comma\"");
        assertThat(CsvReportRenderer.escape("has\"quote")).isEqualTo("\"has\"\"quote\"");
        assertThat(CsvReportRenderer.escape("has\nnewline")).isEqualTo("\"has\nnewline\"");
    }
}
