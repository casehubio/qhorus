package io.casehub.qhorus.compliance.api.core;

import io.casehub.platform.api.signing.document.DocumentVerificationResult;
import io.casehub.platform.api.signing.document.DocumentVerificationService;
import io.casehub.qhorus.compliance.api.ComplianceVerificationResponse;
import io.casehub.qhorus.compliance.format.CsvReportRenderer;
import io.casehub.qhorus.compliance.format.HtmlReportRenderer;
import io.casehub.qhorus.compliance.format.JsonReportRenderer;
import io.casehub.qhorus.compliance.format.PdfReportRenderer;
import io.casehub.qhorus.compliance.report.AttributionReportService;
import io.casehub.qhorus.compliance.report.JudgmentAttributionReportService;
import io.casehub.qhorus.compliance.report.JudgmentFulfillmentReportService;
import io.casehub.qhorus.compliance.report.ObligationReportService;
import io.casehub.qhorus.compliance.report.ProvenanceReportService;
import io.casehub.qhorus.compliance.report.TrustHistoryReportService;
import io.casehub.qhorus.compliance.report.ViolationReportService;
import io.casehub.qhorus.compliance.storage.ComplianceReportRecordStore;
import io.casehub.qhorus.compliance.storage.ComplianceReportStorageService;
import io.casehub.qhorus.compliance.verification.PropertyVerificationService;
import io.casehub.qhorus.runtime.data.DataService;
import io.casehub.qhorus.runtime.identity.InboundTenancyContext;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.UUID;

public class ComplianceReportCore {

    private final AttributionReportService attributionService;
    private final ObligationReportService obligationService;
    private final TrustHistoryReportService trustHistoryService;
    private final ViolationReportService violationService;
    private final ProvenanceReportService provenanceService;
    private final ComplianceReportStorageService storageService;
    private final ComplianceReportRecordStore recordStore;
    private final JsonReportRenderer jsonRenderer;
    private final CsvReportRenderer csvRenderer;
    private final HtmlReportRenderer htmlRenderer;
    private final PdfReportRenderer pdfRenderer;
    private final DocumentVerificationService verificationService;
    private final DataService dataService;
    private final InboundTenancyContext tenancyContext;
    private final JudgmentAttributionReportService judgmentAttributionService;
    private final JudgmentFulfillmentReportService judgmentFulfillmentService;
    private final PropertyVerificationService propertyVerificationService;

    public ComplianceReportCore(AttributionReportService attributionService,
                                ObligationReportService obligationService,
                                TrustHistoryReportService trustHistoryService,
                                ViolationReportService violationService,
                                ProvenanceReportService provenanceService,
                                ComplianceReportStorageService storageService,
                                ComplianceReportRecordStore recordStore,
                                JsonReportRenderer jsonRenderer,
                                CsvReportRenderer csvRenderer,
                                HtmlReportRenderer htmlRenderer,
                                PdfReportRenderer pdfRenderer,
                                DocumentVerificationService verificationService,
                                DataService dataService,
                                InboundTenancyContext tenancyContext,
                                JudgmentAttributionReportService judgmentAttributionService,
                                JudgmentFulfillmentReportService judgmentFulfillmentService,
                                PropertyVerificationService propertyVerificationService) {
        this.attributionService = attributionService;
        this.obligationService = obligationService;
        this.trustHistoryService = trustHistoryService;
        this.violationService = violationService;
        this.provenanceService = provenanceService;
        this.storageService = storageService;
        this.recordStore = recordStore;
        this.jsonRenderer = jsonRenderer;
        this.csvRenderer = csvRenderer;
        this.htmlRenderer = htmlRenderer;
        this.pdfRenderer = pdfRenderer;
        this.verificationService = verificationService;
        this.dataService = dataService;
        this.tenancyContext = tenancyContext;
        this.judgmentAttributionService = judgmentAttributionService;
        this.judgmentFulfillmentService = judgmentFulfillmentService;
        this.propertyVerificationService = propertyVerificationService;
    }

    public record RenderedContent(Object content, String contentType) {}

    public RenderedContent getAttribution(String correlationId, int limit, String accept) {
        var report = attributionService.generate(correlationId, limit, tenancyContext.tenancyId());
        return renderReport(report, accept);
    }

    public RenderedContent getObligations(String channel, String from, String to, String actorId, String accept) {
        UUID channelId = channel != null ? parseChannelId(channel) : null;
        Instant fromInstant = from != null ? Instant.parse(from) : Instant.now().minus(30, ChronoUnit.DAYS);
        Instant toInstant = to != null ? Instant.parse(to) : Instant.now();
        var report = obligationService.generate(channelId, fromInstant, toInstant, actorId, tenancyContext.tenancyId());
        return renderReport(report, accept);
    }

    public RenderedContent getTrustHistory(String actorId, String from, String to, String accept) {
        if (actorId == null) {
            throw new IllegalArgumentException("actorId is required");
        }
        Instant fromInstant = from != null ? Instant.parse(from) : Instant.now().minus(30, ChronoUnit.DAYS);
        Instant toInstant = to != null ? Instant.parse(to) : Instant.now();
        var report = trustHistoryService.generate(actorId, fromInstant, toInstant, tenancyContext.tenancyId());
        return renderReport(report, accept);
    }

    public RenderedContent getViolations(String channel, String from, String to, String accept) {
        if (channel == null) {
            throw new IllegalArgumentException("channel is required");
        }
        Instant fromInstant = from != null ? Instant.parse(from) : Instant.now().minus(30, ChronoUnit.DAYS);
        Instant toInstant = to != null ? Instant.parse(to) : Instant.now();
        var report = violationService.generate(parseChannelId(channel), fromInstant, toInstant, tenancyContext.tenancyId());
        return renderReport(report, accept);
    }

    public RenderedContent getProvenance(String correlationId, int limit) {
        var report = provenanceService.generate(correlationId, limit, tenancyContext.tenancyId());
        return new RenderedContent(jsonRenderer.render(report), "application/ld+json");
    }

    public RenderedContent getJudgmentAttribution(String judgmentId, int limit, String accept) {
        var report = judgmentAttributionService.generate(UUID.fromString(judgmentId), limit, tenancyContext.tenancyId());
        return renderReport(report, accept);
    }

    public RenderedContent getJudgmentFulfillment(String from, String to, String judgmentType, String actorId, String accept) {
        Instant fromInstant = from != null ? Instant.parse(from) : Instant.now().minus(30, ChronoUnit.DAYS);
        Instant toInstant = to != null ? Instant.parse(to) : Instant.now();
        var report = judgmentFulfillmentService.generate(fromInstant, toInstant, judgmentType, actorId, tenancyContext.tenancyId());
        return renderReport(report, accept);
    }

    public RenderedContent getPropertyVerification(String from, String to, String accept) {
        Instant fromInstant = from != null ? Instant.parse(from) : Instant.now().minus(30, ChronoUnit.DAYS);
        Instant toInstant = to != null ? Instant.parse(to) : Instant.now();
        var report = propertyVerificationService.verify(tenancyContext.tenancyId(), fromInstant, toInstant);
        return renderReport(report, accept);
    }

    public Optional<String> getStoredReport(UUID id) {
        return storageService.retrieveJson(id);
    }

    public void deleteStoredReport(UUID id) {
        storageService.delete(id);
    }

    public ComplianceVerificationResponse verifyPdf(byte[] pdfBytes) {
        return toVerificationResponse(verificationService.verifyPdf(pdfBytes));
    }

    public Optional<ComplianceVerificationResponse> verifyStoredReport(UUID id) {
        var recordOpt = recordStore.findById(id);
        if (recordOpt.isEmpty()) return Optional.empty();
        var record = recordOpt.get();
        var dataOpt = dataService.getByUuid(record.artefactId);
        if (dataOpt.isEmpty()) return Optional.empty();
        var data = dataOpt.get();

        DocumentVerificationResult result;
        if (data.binaryContent() != null) {
            result = verificationService.verifyPdf(data.binaryContent());
        } else if (data.content() != null) {
            if (record.signatureArtefactId != null) {
                var sigOpt = dataService.getByUuid(record.signatureArtefactId);
                if (sigOpt.isPresent() && sigOpt.get().binaryContent() != null) {
                    result = verificationService.verifyDetached(
                            data.content().getBytes(), sigOpt.get().binaryContent());
                } else {
                    result = DocumentVerificationResult.unsigned();
                }
            } else {
                result = DocumentVerificationResult.unsigned();
            }
        } else {
            result = DocumentVerificationResult.unsigned();
        }
        return Optional.of(toVerificationResponse(result));
    }

    public Optional<byte[]> downloadSignature(UUID id) {
        var recordOpt = recordStore.findById(id);
        if (recordOpt.isEmpty()) return Optional.empty();
        var record = recordOpt.get();
        if (record.signatureArtefactId == null) return Optional.empty();
        var sigOpt = dataService.getByUuid(record.signatureArtefactId);
        if (sigOpt.isEmpty() || sigOpt.get().binaryContent() == null) return Optional.empty();
        return Optional.of(sigOpt.get().binaryContent());
    }

    private RenderedContent renderReport(Object report, String accept) {
        if (accept != null && accept.contains("application/pdf")) {
            return new RenderedContent(pdfRenderer.render(report), "application/pdf");
        }
        if (accept != null && accept.contains("text/csv")) {
            return new RenderedContent(csvRenderer.render(report), "text/csv");
        }
        if (accept != null && accept.contains("text/html")) {
            return new RenderedContent(htmlRenderer.render(report), "text/html");
        }
        return new RenderedContent(jsonRenderer.render(report), "application/json");
    }

    private static UUID parseChannelId(String channel) {
        try {
            return UUID.fromString(channel);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid channel UUID: " + channel);
        }
    }

    private static ComplianceVerificationResponse toVerificationResponse(DocumentVerificationResult r) {
        var chain = r.certificateChain().stream()
                .map(c -> new ComplianceVerificationResponse.CertificateInfoDto(
                        c.subjectDn(), c.issuerDn(),
                        c.validFrom() != null ? c.validFrom().toString() : null,
                        c.validTo() != null ? c.validTo().toString() : null,
                        c.claimsQualified()))
                .toList();
        return new ComplianceVerificationResponse(
                r.status().name(), r.signerDn(),
                r.signedAt() != null ? r.signedAt().toString() : null,
                r.keyRef(),
                r.detectedProfile() != null ? r.detectedProfile().name() : null,
                chain, r.diagnosticMessage());
    }
}
