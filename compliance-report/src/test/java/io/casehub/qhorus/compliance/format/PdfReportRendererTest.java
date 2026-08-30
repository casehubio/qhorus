package io.casehub.qhorus.compliance.format;

import io.casehub.platform.api.pdf.PdfGenerator;
import io.casehub.platform.api.pdf.PdfOptions;
import io.casehub.platform.pdf.OpenHtmlToPdfGenerator;
import io.casehub.qhorus.compliance.model.AttributionNode;
import io.casehub.qhorus.compliance.model.AttributionReport;
import io.casehub.qhorus.compliance.model.ObligationReport;
import io.casehub.qhorus.compliance.model.ReportFormat;
import io.casehub.qhorus.compliance.model.ViolationReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PdfReportRendererTest {

    @Nested
    class UnitTests {

        PdfReportRenderer renderer;
        PdfGenerator pdfGenerator;

        @BeforeEach
        void setUp() {
            renderer = new PdfReportRenderer();
            renderer.htmlRenderer = new HtmlReportRenderer();
            pdfGenerator = mock(PdfGenerator.class);
            renderer.pdfGenerator = pdfGenerator;
        }

        @Test
        void contentType_isPdf() {
            assertThat(renderer.contentType()).isEqualTo("application/pdf");
        }

        @Test
        void supports_pdf() {
            assertThat(renderer.supports(ReportFormat.PDF)).isTrue();
            assertThat(renderer.supports(ReportFormat.HTML)).isFalse();
            assertThat(renderer.supports(ReportFormat.JSON)).isFalse();
            assertThat(renderer.supports(ReportFormat.CSV)).isFalse();
        }

        @Test
        void render_delegatesToPdfGenerator() {
            byte[] fakePdf = new byte[]{0x25, 0x50, 0x44, 0x46};
            when(pdfGenerator.generateFromHtml(anyString(), any(PdfOptions.class)))
                    .thenReturn(Optional.of(fakePdf));

            var report = new ObligationReport(
                    Instant.now(), Instant.now(), List.of(), List.of(),
                    0, 0, 0, 0, 0, 0, 0, 0.0, null, null, Instant.now(), 1);

            byte[] result = renderer.render(report);
            assertThat(result).isEqualTo(fakePdf);

            var captor = ArgumentCaptor.forClass(PdfOptions.class);
            verify(pdfGenerator).generateFromHtml(anyString(), captor.capture());
            assertThat(captor.getValue().reportType()).isEqualTo("OBLIGATION");
        }

        @Test
        void render_throwsWhenPdfUnavailable() {
            when(pdfGenerator.generateFromHtml(anyString(), any(PdfOptions.class)))
                    .thenReturn(Optional.empty());

            var report = new ObligationReport(
                    Instant.now(), Instant.now(), List.of(), List.of(),
                    0, 0, 0, 0, 0, 0, 0, 0.0, null, null, Instant.now(), 1);

            assertThatThrownBy(() -> renderer.render(report))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("PDF generation unavailable");
        }
    }

    @Nested
    class IntegrationTests {

        PdfReportRenderer renderer;

        @BeforeEach
        void setUp() {
            renderer = new PdfReportRenderer();
            renderer.htmlRenderer = new HtmlReportRenderer();
            renderer.pdfGenerator = new OpenHtmlToPdfGenerator();
        }

        @Test
        void render_attributionReport_producesPdf() {
            var node = new AttributionNode(
                    "e1", "ch1", "channel-a", "COMMAND", "actor-1",
                    "2026-08-27T10:00:00Z", "do X", null, 0,
                    0.85, "SOUND", null, null, null);
            var report = new AttributionReport(
                    "corr-1", "e1", 1, List.of("channel-a"), 500L, "FULFILLED",
                    List.of(node), List.of(), "root", Instant.now(), 1);

            byte[] pdf = renderer.render(report);

            assertThat(pdf).isNotEmpty();
            assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        }

        @Test
        void render_obligationReport_producesPdf() {
            var report = new ObligationReport(
                    Instant.now(), Instant.now(), List.of(), List.of(),
                    10, 8, 1, 1, 0, 0, 0, 0.8, null, null, Instant.now(), 1);

            byte[] pdf = renderer.render(report);

            assertThat(pdf).isNotEmpty();
            assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        }

        @Test
        void render_violationReport_producesPdf() {
            var report = new ViolationReport(
                    Instant.now(), Instant.now(), null, "test-channel",
                    List.of(), 0, 0, 0, java.util.Map.of(),
                    null, Instant.now(), 1);

            byte[] pdf = renderer.render(report);

            assertThat(pdf).isNotEmpty();
            assertThat(new String(pdf, 0, 4)).isEqualTo("%PDF");
        }
    }
}
