package io.casehub.qhorus.compliance.api;

import io.casehub.qhorus.compliance.model.ReportFormat;
import io.casehub.qhorus.compliance.model.ReportType;
import io.casehub.qhorus.compliance.schedule.ComplianceReportSchedule;
import io.casehub.qhorus.compliance.schedule.ComplianceReportScheduleStore;
import io.casehub.qhorus.runtime.identity.InboundTenancyContext;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.UUID;

@Path("/api/compliance/schedules")
@Produces(MediaType.APPLICATION_JSON)
public class ComplianceScheduleResource {

    @Inject ComplianceReportScheduleStore scheduleStore;
    @Inject InboundTenancyContext tenancyContext;

    @GET
    public List<ComplianceReportSchedule> list() {
        return scheduleStore.findByTenancy(tenancyContext.tenancyId());
    }

    @POST
    @Transactional
    public Response create(ScheduleRequest request) {
        if (request.reportType() == ReportType.VIOLATION && request.channelId() == null) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity("channelId is required for VIOLATION report schedules").build();
        }

        ComplianceReportSchedule schedule = new ComplianceReportSchedule();
        schedule.id = UUID.randomUUID();
        schedule.reportType = request.reportType();
        schedule.channelId = request.channelId();
        schedule.scheduleJson = request.schedule();
        schedule.format = request.format() != null ? request.format() : ReportFormat.JSON;
        schedule.tenancyId = tenancyContext.tenancyId();
        schedule.enabled = true;

        scheduleStore.save(schedule);
        return Response.status(Response.Status.CREATED).entity(schedule).build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    public Response update(@PathParam("id") UUID id, ScheduleUpdateRequest request) {
        return scheduleStore.findById(id)
                .map(schedule -> {
                    if (request.schedule() != null) schedule.scheduleJson = request.schedule();
                    if (request.format() != null) schedule.format = request.format();
                    if (request.enabled() != null) schedule.enabled = request.enabled();
                    scheduleStore.save(schedule);
                    return Response.ok(schedule).build();
                })
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    public Response delete(@PathParam("id") UUID id) {
        scheduleStore.delete(id);
        return Response.noContent().build();
    }

    public record ScheduleRequest(ReportType reportType, UUID channelId, String schedule, ReportFormat format) {}
    public record ScheduleUpdateRequest(String schedule, ReportFormat format, Boolean enabled) {}
}
