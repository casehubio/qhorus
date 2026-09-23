package io.casehub.qhorus.a2a.outbound;

import io.casehub.qhorus.a2a.outbound.core.ExternalAgentBindingCore;
import io.casehub.qhorus.api.instance.ExternalAgentBinding;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
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
import java.util.Map;

@Path("/a2a-outbound/bindings")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class ExternalAgentBindingResource {

    @Inject
    ExternalAgentBindingCore core;

    @PUT
    @Path("/{instanceId}")
    public ExternalAgentBinding put(@PathParam("instanceId") String instanceId,
                                    ExternalAgentBindingRequest req) {
        return core.put(instanceId, req);
    }

    @GET
    @Path("/{instanceId}")
    public ExternalAgentBinding get(@PathParam("instanceId") String instanceId) {
        return core.get(instanceId);
    }

    @GET
    public List<ExternalAgentBinding> list() {
        return core.list();
    }

    @DELETE
    @Path("/{instanceId}")
    public Response delete(@PathParam("instanceId") String instanceId) {
        core.delete(instanceId);
        return Response.noContent().build();
    }

    @POST
    @Path("/{instanceId}/verify")
    public Response verify(@PathParam("instanceId") String instanceId) {
        try {
            return core.verify(instanceId)
                    .map(binding -> Response.accepted(binding).build())
                    .orElse(Response.status(Response.Status.NOT_FOUND)
                            .entity(Map.of("error", "Binding not found: " + instanceId))
                            .build());
        } catch (UnsupportedOperationException e) {
            return Response.status(501).entity(Map.of("error", e.getMessage())).build();
        }
    }
}
