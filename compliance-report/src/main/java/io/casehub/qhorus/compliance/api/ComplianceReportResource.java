package io.casehub.qhorus.compliance.api;

import io.casehub.qhorus.compliance.format.CsvReportRenderer;
import io.casehub.qhorus.compliance.format.HtmlReportRenderer;
import io.casehub.qhorus.compliance.format.JsonReportRenderer;
import io.casehub.qhorus.compliance.report.AttributionReportService;
import io.casehub.qhorus.compliance.report.JudgmentAttributionReportService;
import io.casehub.qhorus.compliance.report.JudgmentFulfillmentReportService;
import io.casehub.qhorus.compliance.report.ObligationReportService;
import io.casehub.qhorus.compliance.report.ProvenanceReportService;
import io.casehub.qhorus.compliance.report.TrustHistoryReportService;
import io.casehub.qhorus.compliance.report.ViolationReportService;
import io.casehub.qhorus.compliance.storage.ComplianceReportRecordStore;
import io.casehub.qhorus.compliance.storage.ComplianceReportStorageService;
import io.casehub.qhorus.runtime.identity.InboundTenancyContext;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Response;

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

    private Response renderResponse(Object report, String accept) {
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
