package io.casehub.qhorus.compliance.api;

import io.casehub.platform.api.signing.document.DocumentVerificationResult;
import io.casehub.platform.api.signing.document.DocumentVerificationService;
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
import io.casehub.qhorus.runtime.data.DataService;
import io.casehub.qhorus.runtime.identity.InboundTenancyContext;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.multipart.FileUpload;
import org.jboss.resteasy.reactive.RestForm;

import java.io.IOException;
import java.nio.file.Files;
import java.time.Instant;
import java.util.UUID;

@Path("/api/compliance")
public class ComplianceReportResource {

    @Inject AttributionReportService attributionService;
    @Inject ObligationReportService obligationService;
    @Inject TrustHistoryReportService trustHistoryService;
    @Inject ViolationReportService violationService;
    @Inject ProvenanceReportService provenanceService;
    @Inject ComplianceReportStorageService storageService;
    @Inject ComplianceReportRecordStore recordStore;
    @Inject JsonReportRenderer jsonRenderer;
    @Inject CsvReportRenderer csvRenderer;
    @Inject HtmlReportRenderer htmlRenderer;
    @Inject PdfReportRenderer pdfRenderer;
    @Inject DocumentVerificationService verificationService;
    @Inject DataService dataService;
    @Inject InboundTenancyContext tenancyContext;
    @Inject
            JudgmentAttributionReportService judgmentAttributionService;
    @Inject
            JudgmentFulfillmentReportService judgmentFulfillmentService;
    @Inject
    io.casehub.qhorus.compliance.verification.PropertyVerificationService propertyVerificationService;


    @GET
    @Path("/attribution/{correlationId}")
    public Response getAttribution(
            @PathParam("correlationId") String correlationId,
            @QueryParam("limit") @DefaultValue("200") int limit,
            @HeaderParam("Accept") @DefaultValue("application/json") String accept) {
        var report = attributionService.generate(correlationId, limit, tenancyContext.tenancyId());
        return renderResponse(report, accept);
    }

    @GET
    @Path("/obligations")
    public Response getObligations(
            @QueryParam("channel") String channel,
            @QueryParam("from") String from,
            @QueryParam("to") String to,
            @QueryParam("actorId") String actorId,
            @HeaderParam("Accept") @DefaultValue("application/json") String accept) {
        UUID channelId = channel != null ? parseChannelId(channel) : null;
        Instant fromInstant = from != null ? Instant.parse(from) : Instant.now().minus(30, java.time.temporal.ChronoUnit.DAYS);
        Instant toInstant = to != null ? Instant.parse(to) : Instant.now();
        var report = obligationService.generate(
                channelId, fromInstant, toInstant, actorId, tenancyContext.tenancyId());
        return renderResponse(report, accept);
    }

    @GET
    @Path("/trust-history")
    public Response getTrustHistory(
            @QueryParam("actorId") String actorId,
            @QueryParam("from") String from,
            @QueryParam("to") String to,
            @HeaderParam("Accept") @DefaultValue("application/json") String accept) {
        if (actorId == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("actorId is required").build();
        }
        Instant fromInstant = from != null ? Instant.parse(from) : Instant.now().minus(30, java.time.temporal.ChronoUnit.DAYS);
        Instant toInstant = to != null ? Instant.parse(to) : Instant.now();
        var report = trustHistoryService.generate(
                actorId, fromInstant, toInstant, tenancyContext.tenancyId());
        return renderResponse(report, accept);
    }

    @GET
    @Path("/violations")
    public Response getViolations(
            @QueryParam("channel") String channel,
            @QueryParam("from") String from,
            @QueryParam("to") String to,
            @HeaderParam("Accept") @DefaultValue("application/json") String accept) {
        if (channel == null) {
            return Response.status(Response.Status.BAD_REQUEST).entity("channel is required").build();
        }
        Instant fromInstant = from != null ? Instant.parse(from) : Instant.now().minus(30, java.time.temporal.ChronoUnit.DAYS);
        Instant toInstant = to != null ? Instant.parse(to) : Instant.now();
        var report = violationService.generate(
                parseChannelId(channel), fromInstant, toInstant, tenancyContext.tenancyId());
        return renderResponse(report, accept);
    }

    @GET
    @Path("/provenance/{correlationId}")
    @Produces("application/json")
    public Response getProvenance(
            @PathParam("correlationId") String correlationId,
            @QueryParam("limit") @DefaultValue("200") int limit) {
        var report = provenanceService.generate(correlationId, limit, tenancyContext.tenancyId());
        return Response.ok(jsonRenderer.render(report))
                .header("Content-Type", "application/ld+json")
                .build();
    }


    @GET
    @Path("/judgment-attribution/{judgmentId}")
    public Response getJudgmentAttribution(
            @PathParam("judgmentId") String judgmentId,
            @QueryParam("limit") @DefaultValue("200") int limit,
            @HeaderParam("Accept") @DefaultValue("application/json") String accept) {
        var report = judgmentAttributionService.generate(
                UUID.fromString(judgmentId), limit, tenancyContext.tenancyId());
        return renderResponse(report, accept);
    }

    @GET
    @Path("/judgment-fulfillment")
    public Response getJudgmentFulfillment(
            @QueryParam("from") String from,
            @QueryParam("to") String to,
            @QueryParam("judgmentType") String judgmentType,
            @QueryParam("actorId") String actorId,
            @HeaderParam("Accept") @DefaultValue("application/json") String accept) {
        Instant fromInstant = from != null ? Instant.parse(from) : Instant.now().minus(30, java.time.temporal.ChronoUnit.DAYS);
        Instant toInstant = to != null ? Instant.parse(to) : Instant.now();
        var report = judgmentFulfillmentService.generate(
                fromInstant, toInstant, judgmentType, actorId, tenancyContext.tenancyId());
        return renderResponse(report, accept);
    }

    @GET
    @Path("/property-verification")
    public Response getPropertyVerification(
            @QueryParam("from") String from,
            @QueryParam("to") String to,
            @HeaderParam("Accept") @DefaultValue("application/json") String accept) {
        Instant fromInstant = from != null ? Instant.parse(from) : Instant.now().minus(30, java.time.temporal.ChronoUnit.DAYS);
        Instant toInstant   = to != null ? Instant.parse(to) : Instant.now();
        var     report      = propertyVerificationService.verify(tenancyContext.tenancyId(), fromInstant, toInstant);
        return renderResponse(report, accept);
    }


    @GET
    @Path("/reports/{id}")
    public Response getStoredReport(
            @PathParam("id") UUID id,
            @HeaderParam("Accept") @DefaultValue("application/json") String accept) {
        var jsonOpt = storageService.retrieveJson(id);
        if (jsonOpt.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(jsonOpt.get()).header("Content-Type", "application/json").build();
    }

    @DELETE
    @Path("/reports/{id}")
    public Response deleteStoredReport(@PathParam("id") UUID id) {
        storageService.delete(id);
        return Response.noContent().build();
    }

    @POST
    @Path("/verify")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response verifyUpload(@RestForm("file") FileUpload file) {
        try {
            byte[] bytes = Files.readAllBytes(file.filePath());
            String filename = file.fileName();
            DocumentVerificationResult result;
            if (filename != null && filename.endsWith(".p7s")) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("Detached signature verification requires both data and signature files")
                        .build();
            }
            result = verificationService.verifyPdf(bytes);
            return Response.ok(toVerificationResponse(result)).build();
        } catch (IOException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Failed to read uploaded file").build();
        }
    }

    @GET
    @Path("/reports/{id}/verify")
    @Produces(MediaType.APPLICATION_JSON)
    public Response verifyStoredReport(@PathParam("id") UUID id) {
        var recordOpt = recordStore.findById(id);
        if (recordOpt.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        var record = recordOpt.get();
        var dataOpt = dataService.getByUuid(record.artefactId);
        if (dataOpt.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).entity("Report artefact not found").build();
        }
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
        return Response.ok(toVerificationResponse(result)).build();
    }

    @GET
    @Path("/reports/{id}/signature")
    public Response downloadSignature(@PathParam("id") UUID id) {
        var recordOpt = recordStore.findById(id);
        if (recordOpt.isEmpty()) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        var record = recordOpt.get();
        if (record.signatureArtefactId == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("No detached signature for this report").build();
        }
        var sigOpt = dataService.getByUuid(record.signatureArtefactId);
        if (sigOpt.isEmpty() || sigOpt.get().binaryContent() == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity("Signature artefact not found").build();
        }
        return Response.ok(sigOpt.get().binaryContent())
                .header("Content-Type", "application/pkcs7-signature")
                .header("Content-Disposition", "attachment; filename=\"report-" + id + ".p7s\"")
                .build();
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
                r.status().name(),
                r.signerDn(),
                r.signedAt() != null ? r.signedAt().toString() : null,
                r.keyRef(),
                r.detectedProfile() != null ? r.detectedProfile().name() : null,
                chain,
                r.diagnosticMessage());
    }

    private Response renderResponse(Object report, String accept) {
        if (accept != null && accept.contains("application/pdf")) {
            return Response.ok(pdfRenderer.render(report))
                    .header("Content-Type", "application/pdf").build();
        }
        if (accept != null && accept.contains("text/csv")) {
            return Response.ok(csvRenderer.render(report))
                    .header("Content-Type", "text/csv").build();
        }
        if (accept != null && accept.contains("text/html")) {
            return Response.ok(htmlRenderer.render(report))
                    .header("Content-Type", "text/html").build();
        }
        return Response.ok(jsonRenderer.render(report))
                .header("Content-Type", "application/json").build();
    }

    private static UUID parseChannelId(String channel) {
        try {
            return UUID.fromString(channel);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid channel UUID: " + channel);
        }
    }
}
