package io.casehub.qhorus.compliance.api;

import io.casehub.qhorus.compliance.api.core.ComplianceReportCore;
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

import io.casehub.platform.api.mcp.HandWrittenEndpoint;

import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;

@HandWrittenEndpoint("multipart upload, content negotiation, binary signature downloads")
@Path("/api/compliance")
public class ComplianceReportResource {

    @Inject ComplianceReportCore core;


    @GET
    @Path("/attribution/{correlationId}")
    public Response getAttribution(
            @PathParam("correlationId") String correlationId,
            @QueryParam("limit") @DefaultValue("200") int limit,
            @HeaderParam("Accept") @DefaultValue("application/json") String accept) {
        return toResponse(core.getAttribution(correlationId, limit, accept));
    }

    @GET
    @Path("/obligations")
    public Response getObligations(
            @QueryParam("channel") String channel,
            @QueryParam("from") String from,
            @QueryParam("to") String to,
            @QueryParam("actorId") String actorId,
            @HeaderParam("Accept") @DefaultValue("application/json") String accept) {
        return toResponse(core.getObligations(channel, from, to, actorId, accept));
    }

    @GET
    @Path("/trust-history")
    public Response getTrustHistory(
            @QueryParam("actorId") String actorId,
            @QueryParam("from") String from,
            @QueryParam("to") String to,
            @HeaderParam("Accept") @DefaultValue("application/json") String accept) {
        try {
            return toResponse(core.getTrustHistory(actorId, from, to, accept));
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @GET
    @Path("/violations")
    public Response getViolations(
            @QueryParam("channel") String channel,
            @QueryParam("from") String from,
            @QueryParam("to") String to,
            @HeaderParam("Accept") @DefaultValue("application/json") String accept) {
        try {
            return toResponse(core.getViolations(channel, from, to, accept));
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @GET
    @Path("/provenance/{correlationId}")
    @Produces("application/json")
    public Response getProvenance(
            @PathParam("correlationId") String correlationId,
            @QueryParam("limit") @DefaultValue("200") int limit) {
        return toResponse(core.getProvenance(correlationId, limit));
    }

    @GET
    @Path("/judgment-attribution/{judgmentId}")
    public Response getJudgmentAttribution(
            @PathParam("judgmentId") String judgmentId,
            @QueryParam("limit") @DefaultValue("200") int limit,
            @HeaderParam("Accept") @DefaultValue("application/json") String accept) {
        return toResponse(core.getJudgmentAttribution(judgmentId, limit, accept));
    }

    @GET
    @Path("/judgment-fulfillment")
    public Response getJudgmentFulfillment(
            @QueryParam("from") String from,
            @QueryParam("to") String to,
            @QueryParam("judgmentType") String judgmentType,
            @QueryParam("actorId") String actorId,
            @HeaderParam("Accept") @DefaultValue("application/json") String accept) {
        return toResponse(core.getJudgmentFulfillment(from, to, judgmentType, actorId, accept));
    }

    @GET
    @Path("/property-verification")
    public Response getPropertyVerification(
            @QueryParam("from") String from,
            @QueryParam("to") String to,
            @HeaderParam("Accept") @DefaultValue("application/json") String accept) {
        return toResponse(core.getPropertyVerification(from, to, accept));
    }

    @GET
    @Path("/reports/{id}")
    public Response getStoredReport(@PathParam("id") UUID id) {
        return core.getStoredReport(id)
                .map(json -> Response.ok(json).header("Content-Type", "application/json").build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @DELETE
    @Path("/reports/{id}")
    public Response deleteStoredReport(@PathParam("id") UUID id) {
        core.deleteStoredReport(id);
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
            if (filename != null && filename.endsWith(".p7s")) {
                return Response.status(Response.Status.BAD_REQUEST)
                        .entity("Detached signature verification requires both data and signature files")
                        .build();
            }
            return Response.ok(core.verifyPdf(bytes)).build();
        } catch (IOException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("Failed to read uploaded file").build();
        }
    }

    @GET
    @Path("/reports/{id}/verify")
    @Produces(MediaType.APPLICATION_JSON)
    public Response verifyStoredReport(@PathParam("id") UUID id) {
        return core.verifyStoredReport(id)
                .map(r -> Response.ok(r).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @GET
    @Path("/reports/{id}/signature")
    public Response downloadSignature(@PathParam("id") UUID id) {
        return core.downloadSignature(id)
                .map(bytes -> Response.ok(bytes)
                        .header("Content-Type", "application/pkcs7-signature")
                        .header("Content-Disposition", "attachment; filename=\"report-" + id + ".p7s\"")
                        .build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    private static Response toResponse(ComplianceReportCore.RenderedContent rendered) {
        return Response.ok(rendered.content()).header("Content-Type", rendered.contentType()).build();
    }
}
