package io.casehub.qhorus.compliance.core.format;

import io.casehub.platform.api.pdf.PdfAConformance;
import io.casehub.platform.api.pdf.PdfGenerator;
import io.casehub.platform.api.pdf.PdfOptions;
import io.casehub.qhorus.api.compliance.report.ReportFormat;

public class PdfReportRenderer implements ReportRenderer {

    private final HtmlReportRenderer htmlRenderer;
    private final PdfGenerator pdfGenerator;

    public PdfReportRenderer(HtmlReportRenderer htmlRenderer, PdfGenerator pdfGenerator) {
        this.htmlRenderer = htmlRenderer;
        this.pdfGenerator = pdfGenerator;
    }

    @Override
    public String contentType() {
        return "application/pdf";
    }

    @Override
    public byte[] render(Object report) {
        PdfDocumentMetadata metadata = PdfDocumentMetadata.fromReport(report);
        String html = htmlRenderer.renderForPdf(report, metadata);
        PdfOptions options = new PdfOptions(
                metadata.title(), metadata.author(), metadata.createdAt(),
                metadata.reportType(), PdfAConformance.PDFA_2_B);
        return pdfGenerator.generateFromHtml(html, options)
                .orElseThrow(() -> new IllegalStateException(
                        "PDF generation unavailable — casehub-platform-pdf not on classpath"));
    }

    @Override
    public boolean supports(ReportFormat format) {
        return format == ReportFormat.PDF;
    }
}
