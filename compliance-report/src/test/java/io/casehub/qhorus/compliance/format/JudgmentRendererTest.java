package io.casehub.qhorus.compliance.format;

import io.casehub.qhorus.compliance.model.CallerSummary;
import io.casehub.qhorus.compliance.model.JudgmentAttributionReport;
import io.casehub.qhorus.compliance.model.JudgmentEvent;
import io.casehub.qhorus.compliance.model.JudgmentFulfillmentReport;
import io.casehub.qhorus.compliance.model.JudgmentTypeSummary;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JudgmentRendererTest {

    private final CsvReportRenderer csvRenderer = new CsvReportRenderer();
    private final HtmlReportRenderer htmlRenderer = new HtmlReportRenderer();

    @Test
    void csvRendersJudgmentAttributionWithReasoningColumn() {
        var report = sampleAttributionReport();
        byte[] csv = csvRenderer.render(report);
        String output = new String(csv);

        assertThat(output).startsWith("eventKind,");
        assertThat(output).contains("reasoning");
        assertThat(output).contains("judgment_responded");
        assertThat(output).contains("I chose this because the diff was clean");
    }

    @Test
    void csvRendersJudgmentFulfillmentWithTwoSections() {
        var report = sampleFulfillmentReport();
        byte[] csv = csvRenderer.render(report);
        String output = new String(csv);

        assertThat(output).contains("judgmentType,total,accepted");
        assertThat(output).contains("actorId,total,accepted");
        assertThat(output).contains("code_review");
        assertThat(output).contains("agent-reviewer");
    }

    @Test
    void htmlRendersJudgmentAttributionWithReasoningColumn() {
        var report = sampleAttributionReport();
        byte[] html = htmlRenderer.render(report);
        String output = new String(html);

        assertThat(output).contains("<th>Reasoning</th>");
        assertThat(output).contains("I chose this because the diff was clean");
        assertThat(output).contains("Judgment Attribution Report");
    }

    @Test
    void htmlRendersJudgmentFulfillmentWithByTypeAndByCaller() {
        var report = sampleFulfillmentReport();
        byte[] html = htmlRenderer.render(report);
        String output = new String(html);

        assertThat(output).contains("By Type");
        assertThat(output).contains("By Caller");
        assertThat(output).contains("code_review");
        assertThat(output).contains("Judgment Fulfillment Report");
    }

    @Test
    void htmlEscapesReasoningContent() {
        var event = new JudgmentEvent("judgment_responded", "agent-1",
                Instant.now(), 0.8, null, null, 0.9, 100L,
                "<script>alert('xss')</script>");
        var report = new JudgmentAttributionReport(
                "j1", "code_review", 1, List.of("ch1"), "corr1", null, 100L,
                List.of(event), List.of(), List.of(), null, Instant.now(), 1);

        byte[] html = htmlRenderer.render(report);
        String output = new String(html);

        assertThat(output).doesNotContain("<script>");
        assertThat(output).contains("&lt;script&gt;");
    }

    @Test
    void csvEscapesReasoningWithCommas() {
        var event = new JudgmentEvent("judgment_responded", "agent-1",
                Instant.now(), 0.8, null, null, 0.9, 100L,
                "I chose X, because Y was worse");
        var report = new JudgmentAttributionReport(
                "j1", "code_review", 1, List.of("ch1"), "corr1", null, 100L,
                List.of(event), List.of(), List.of(), null, Instant.now(), 1);

        byte[] csv = csvRenderer.render(report);
        String output = new String(csv);

        assertThat(output).contains("\"I chose X, because Y was worse\"");
    }

    private JudgmentAttributionReport sampleAttributionReport() {
        var events = List.of(
                new JudgmentEvent("judgment_yielded", "engine", Instant.now(),
                        null, null, null, 0.9, null, null),
                new JudgmentEvent("judgment_responded", "agent-reviewer", Instant.now(),
                        0.85, null, null, 0.8, 60000L,
                        "I chose this because the diff was clean"),
                new JudgmentEvent("judgment_verified", "engine", Instant.now(),
                        null, "ACCEPTED", null, 0.9, 5000L, null));
        return new JudgmentAttributionReport(
                "j1", "code_review", 1, List.of("ch1"), "corr1",
                "ACCEPTED", 65000L, events, List.of(), List.of(),
                null, Instant.now(), 1);
    }

    private JudgmentFulfillmentReport sampleFulfillmentReport() {
        var byType = List.of(new JudgmentTypeSummary(
                "code_review", 10, 8, 1, 1, 0, 0.8, 30000, 0.75));
        var byCaller = List.of(new CallerSummary(
                "agent-reviewer", 10, 8, 1, 1, 0, 0.8, 30000, 0.75, 0.85));
        return new JudgmentFulfillmentReport(
                Instant.now().minusSeconds(86400), Instant.now(),
                byType, byCaller, 10, 8, 1, 1, 0, 0.8, 30000, 0.75,
                null, Instant.now(), 1);
    }
}
