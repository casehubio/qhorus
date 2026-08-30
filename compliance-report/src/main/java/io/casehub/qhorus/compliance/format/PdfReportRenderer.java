package io.casehub.qhorus.compliance.format;

import io.casehub.platform.api.pdf.PdfAConformance;
import io.casehub.platform.api.pdf.PdfGenerator;
import io.casehub.platform.api.pdf.PdfOptions;
import io.casehub.qhorus.compliance.model.ReportFormat;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class PdfReportRenderer implements ReportRenderer {

    @Inject HtmlReportRenderer htmlRenderer;
    @Inject PdfGenerator pdfGenerator;

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
