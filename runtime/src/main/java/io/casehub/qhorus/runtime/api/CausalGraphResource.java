package io.casehub.qhorus.runtime.api;

import io.casehub.qhorus.runtime.api.core.CausalGraphCore;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;
import java.util.UUID;

@Path("/api/causal-graph")
@Produces(MediaType.APPLICATION_JSON)
public class CausalGraphResource {

    @Inject
    CausalGraphCore core;

    @GET
    @Path("/{correlationId}")
    public Response getGraph(@PathParam("correlationId") String correlationId,
                             @QueryParam("limit") @DefaultValue("100") int limit) {
        return Response.ok(core.getGraph(correlationId, limit)).build();
    }

    @GET
    @Path("/attribution/{entryId}")
    public Response getAttribution(@PathParam("entryId") String entryId) {
        return Response.ok(core.getAttribution(entryId)).build();
    }

    @Produces(MediaType.APPLICATION_JSON)
    public Response handleIllegalArgument(IllegalArgumentException e) {
        return Response.status(400)
                .entity(Map.of("error", e.getMessage()))
                .build();
    }
}
