package io.casehub.qhorus.runtime.api;

import io.casehub.qhorus.runtime.api.core.AgentCardCore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

@Path("/.well-known")
@ApplicationScoped
public class AgentCardResource {

    @Inject
    AgentCardCore core;

    @GET
    @Path("/agent.json")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getAgentCard() {
        return Response.ok(core.getAgentCard()).build();
    }

    @GET
    @Path("/agents/{instanceId}.json")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getPerAgentCard(@PathParam("instanceId") String instanceId) {
        return core.getPerAgentCard(instanceId)
                .map(card -> Response.ok(card).build())
                .orElse(Response.status(Response.Status.NOT_FOUND)
                        .entity(new ErrorResponse("Instance not found: " + instanceId))
                        .type(MediaType.APPLICATION_JSON).build());
    }

    @GET
    @Path("/jwks.json")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getJwks() {
        return core.getJwks()
                .map(jwks -> Response.ok(jwks)
                        .header("Cache-Control", "public, max-age=86400")
                        .header("Access-Control-Allow-Origin", "*")
                        .header("Access-Control-Allow-Methods", "GET")
                        .header("Access-Control-Allow-Headers", "Accept")
                        .build())
                .orElse(Response.status(Response.Status.NOT_FOUND).build());
    }
}
