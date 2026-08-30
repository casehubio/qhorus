package io.casehub.qhorus.compliance.format;

import io.casehub.qhorus.compliance.model.AttributionReport;
import io.casehub.qhorus.compliance.model.JudgmentAttributionReport;
import io.casehub.qhorus.compliance.model.JudgmentFulfillmentReport;
import io.casehub.qhorus.compliance.model.ObligationReport;
import io.casehub.qhorus.compliance.model.ProvenanceReport;
import io.casehub.qhorus.compliance.model.PropertyVerificationReport;
import io.casehub.qhorus.compliance.model.TrustHistoryReport;
import io.casehub.qhorus.compliance.model.ViolationReport;

import java.time.Instant;

public record PdfDocumentMetadata(
        String title,
        String author,
        Instant createdAt,
        String reportType,
        String tenancyId) {

    static PdfDocumentMetadata fromReport(Object report) {
        return switch (report) {
            case AttributionReport r -> new PdfDocumentMetadata(
                    "Attribution Report — " + r.correlationId(),
                    "CaseHub Compliance", r.generatedAt(), "ATTRIBUTION", null);
            case ObligationReport r -> new PdfDocumentMetadata(
                    "Obligation Fulfillment Report",
                    "CaseHub Compliance", r.generatedAt(), "OBLIGATION", null);
            case ViolationReport r -> new PdfDocumentMetadata(
                    "Violation Report — " + r.channelName(),
                    "CaseHub Compliance", r.generatedAt(), "VIOLATION", null);
            case TrustHistoryReport r -> new PdfDocumentMetadata(
                    "Trust History Report",
                    "CaseHub Compliance", r.generatedAt(), "TRUST_HISTORY", null);
            case ProvenanceReport r -> new PdfDocumentMetadata(
                    "Provenance Report — " + r.correlationId(),
                    "CaseHub Compliance", r.generatedAt(), "PROVENANCE", null);
            case JudgmentAttributionReport r -> new PdfDocumentMetadata(
                    "Judgment Attribution Report — " + r.judgmentId(),
                    "CaseHub Compliance", r.generatedAt(), "JUDGMENT_ATTRIBUTION", null);
            case JudgmentFulfillmentReport r -> new PdfDocumentMetadata(
                    "Judgment Fulfillment Report",
                    "CaseHub Compliance", r.generatedAt(), "JUDGMENT_FULFILLMENT", null);
            case PropertyVerificationReport r -> new PdfDocumentMetadata(
                    "Property Verification Report",
                    "CaseHub Compliance", r.generatedAt(), "PROPERTY_VERIFICATION", null);
            default -> new PdfDocumentMetadata(
                    "Compliance Report", "CaseHub Compliance", Instant.now(),
                    report.getClass().getSimpleName(), null);
        };
    }
}
