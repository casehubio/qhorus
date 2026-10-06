package io.casehub.qhorus.runtime.api;

import io.casehub.qhorus.runtime.api.core.ErrorResponse;
import io.casehub.qhorus.runtime.api.core.InstanceCore;
import io.casehub.qhorus.runtime.api.core.InstanceResponse;
import io.casehub.qhorus.runtime.api.core.RegisterInstanceRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.NoSuchElementException;

@Path("/api/instances")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class InstanceResource {

    @Inject InstanceCore core;

    @POST
    public Response register(RegisterInstanceRequest request) {
        try {
            InstanceResponse resp = core.register(request);
            return Response.status(Response.Status.CREATED).entity(resp).build();
        } catch (IllegalArgumentException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ErrorResponse(e.getMessage())).build();
        }
    }

    @DELETE
    @Path("/{instanceId}")
    public Response deregister(@PathParam("instanceId") String instanceId) {
        try {
            core.deregister(instanceId);
            return Response.noContent().build();
        } catch (NoSuchElementException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse(e.getMessage())).build();
        }
    }

    @GET
    public List<InstanceResponse> list(@QueryParam("capability") String capability) {
        return core.list(capability);
    }

    @GET
    @Path("/{instanceId}")
    public Response get(@PathParam("instanceId") String instanceId) {
        try {
            return Response.ok(core.get(instanceId)).build();
        } catch (NoSuchElementException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ErrorResponse(e.getMessage())).build();
        }
    }
}
