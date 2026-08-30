package io.casehub.qhorus.compliance.format;

import io.casehub.qhorus.compliance.model.AttributionEdge;
import io.casehub.qhorus.compliance.model.AttributionNode;
import io.casehub.qhorus.compliance.model.AttributionReport;
import io.casehub.qhorus.compliance.model.ReportFormat;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HtmlReportRendererTest {

    final HtmlReportRenderer renderer = new HtmlReportRenderer();

    @Test
    void render_attributionReport_producesValidHtml() {
        var node = new AttributionNode(
                "e1", "ch1", "channel-a", "COMMAND", "actor-1",
                "2026-08-27T10:00:00Z", "do X", null, 0,
                0.85, "SOUND", null, null, null);
        var report = new AttributionReport(
                "corr-1", "e1", 1, List.of("channel-a"), 500L, "FULFILLED",
                List.of(node), List.of(), "root", Instant.now(), 1);

        String html = new String(renderer.render(report), StandardCharsets.UTF_8);

        assertThat(html).contains("<!DOCTYPE html>");
        assertThat(html).contains("<table>");
        assertThat(html).contains("<thead>");
        assertThat(html).contains("<tbody>");
        assertThat(html).contains("</html>");
        assertThat(html).contains("channel-a");
        assertThat(html).contains("COMMAND");
        assertThat(html).contains("SOUND");
    }

    @Test
    void render_includesPrintFriendlyCss() {
        var report = new AttributionReport(
                "corr-1", null, 0, List.of(), null, "OPEN",
                List.of(), List.of(), null, Instant.now(), 1);

        String html = new String(renderer.render(report), StandardCharsets.UTF_8);

        assertThat(html).contains("@media print");
    }

    @Test
    void render_escapesHtmlEntities() {
        var node = new AttributionNode(
                "e1", "ch1", "channel-a", "COMMAND", "<script>alert('xss')</script>",
                "2026-08-27T10:00:00Z", "content", null, 0,
                null, null, null, null, null);
        var report = new AttributionReport(
                "corr-1", "e1", 1, List.of("ch"), 0L, "OPEN",
                List.of(node), List.of(), null, Instant.now(), 1);

        String html = new String(renderer.render(report), StandardCharsets.UTF_8);

        assertThat(html).doesNotContain("<script>");
        assertThat(html).contains("&lt;script&gt;");
    }

    @Test
    void supports_html() {
        assertThat(renderer.supports(ReportFormat.HTML)).isTrue();
        assertThat(renderer.supports(ReportFormat.JSON)).isFalse();
        assertThat(renderer.supports(ReportFormat.CSV)).isFalse();
    }

    @Test
    void renderForPdf_includesPageCssRules() {
        var report = new AttributionReport(
                "corr-1", null, 0, List.of(), null, "OPEN",
                List.of(), List.of(), null, Instant.now(), 1);
        var metadata = new PdfDocumentMetadata(
                "Attribution Report", "CaseHub Compliance",
                Instant.now(), "ATTRIBUTION", "tenant-1");

        String html = renderer.renderForPdf(report, metadata);

        assertThat(html).contains("@page");
        assertThat(html).contains("counter(page)");
        assertThat(html).contains("counter(pages)");
    }

    @Test
    void renderForPdf_includesHeaderAndFooter() {
        var report = new AttributionReport(
                "corr-1", null, 0, List.of(), null, "OPEN",
                List.of(), List.of(), null, Instant.now(), 1);
        var metadata = new PdfDocumentMetadata(
                "Attribution Report", "CaseHub Compliance",
                Instant.parse("2026-08-30T12:00:00Z"), "ATTRIBUTION", "tenant-1");

        String html = renderer.renderForPdf(report, metadata);

        assertThat(html).contains("ATTRIBUTION");
        assertThat(html).contains("tenant-1");
        assertThat(html).contains("pdf-header");
        assertThat(html).contains("pdf-footer");
    }

    @Test
    void renderForPdf_containsReportBody() {
        var node = new AttributionNode(
                "e1", "ch1", "channel-a", "COMMAND", "actor-1",
                "2026-08-27T10:00:00Z", "do X", null, 0,
                0.85, "SOUND", null, null, null);
        var report = new AttributionReport(
                "corr-1", "e1", 1, List.of("channel-a"), 500L, "FULFILLED",
                List.of(node), List.of(), "root", Instant.now(), 1);
        var metadata = new PdfDocumentMetadata(
                "Attribution Report", "CaseHub Compliance",
                Instant.now(), "ATTRIBUTION", null);

        String html = renderer.renderForPdf(report, metadata);

        assertThat(html).contains("channel-a");
        assertThat(html).contains("COMMAND");
        assertThat(html).contains("<table>");
    }

    @Test
    void render_unchanged_noPdfCss() {
        var report = new AttributionReport(
                "corr-1", null, 0, List.of(), null, "OPEN",
                List.of(), List.of(), null, Instant.now(), 1);

        String html = new String(renderer.render(report), StandardCharsets.UTF_8);

        assertThat(html).doesNotContain("@page");
        assertThat(html).doesNotContain("counter(page)");
        assertThat(html).doesNotContain("pdf-header");
    }
}
