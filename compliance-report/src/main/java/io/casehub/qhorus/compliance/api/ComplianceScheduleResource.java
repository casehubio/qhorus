package io.casehub.qhorus.compliance.api;

import io.casehub.qhorus.compliance.api.core.ComplianceScheduleCore;
import io.casehub.qhorus.compliance.schedule.ComplianceReportSchedule;
import jakarta.inject.Inject;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import io.casehub.platform.api.mcp.HandWrittenEndpoint;

import java.util.List;
import java.util.UUID;

@HandWrittenEndpoint("CRUD resource paired with hand-written ComplianceReportResource")
@Path("/api/compliance/schedules")
@Produces(MediaType.APPLICATION_JSON)
public class ComplianceScheduleResource {

    @Inject ComplianceScheduleCore core;

    @GET
    public List<ComplianceReportSchedule> list() {
        return core.list();
    }

    @POST
    public Response create(io.casehub.qhorus.compliance.api.core.ScheduleRequest request) {
        try {
            var schedule = core.create(request);
            return Response.status(Response.Status.CREATED).entity(schedule).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST).entity(e.getMessage()).build();
        }
    }

    @PUT
    @Path("/{id}")
    public Response update(@PathParam("id") UUID id, io.casehub.qhorus.compliance.api.core.ScheduleUpdateRequest request) {
        return core.update(id, request)
                .map(schedule -> Response.ok(schedule).build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @DELETE
    @Path("/{id}")
    public Response delete(@PathParam("id") UUID id) {
        core.delete(id);
        return Response.noContent().build();
    }
}
